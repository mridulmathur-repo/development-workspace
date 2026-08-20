package com.workflowautomation.jiraplanner.jira;

import com.workflowautomation.jiraplanner.config.JiraProperties;
import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import com.workflowautomation.jiraplanner.planning.StoryPlan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JiraRestClient implements JiraGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraRestClient.class);
    private static final Pattern SEQUENCE_LABEL = Pattern.compile("ai-(story|dev|test)-(\\d+)", Pattern.CASE_INSENSITIVE);

    private final JiraProperties properties;
    private final RestClient restClient;
    private final AtlassianDocumentFactory documents;

    public JiraRestClient(JiraProperties properties, RestClient.Builder builder, AtlassianDocumentFactory documents) {
        this.properties = properties;
        this.documents = documents;
        String credentials = properties.getEmail() + ":" + properties.getApiToken();
        String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorization)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<String> findReadyEpicKeys(int limit) {
        requireConfigured();
        String jql = "project = \"%s\" AND issuetype = \"%s\" AND status = \"%s\" "
                + "AND (labels is EMPTY OR labels not in (\"ai-planned\")) ORDER BY created ASC";
        jql = jql.formatted(properties.getProjectKey(), properties.getEpicIssueType(), properties.getReadyStatus());
        JsonNode response = search(jql, Math.max(1, limit), List.of("key"));
        List<String> keys = new ArrayList<>();
        response.path("issues").forEach(issue -> keys.add(issue.path("key").asText()));
        return keys.stream().filter(key -> !key.isBlank()).toList();
    }

    @Override
    public EpicRequirement getEpic(String epicKey) {
        requireConfigured();
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/api/3/issue/{key}")
                        .queryParam("fields", "summary,description,status,labels,project,issuetype")
                        .build(epicKey))
                .retrieve()
                .body(JsonNode.class);
        JsonNode fields = response.path("fields");
        Set<String> labels = new LinkedHashSet<>();
        fields.path("labels").forEach(label -> labels.add(label.asText()));
        return new EpicRequirement(
                response.path("key").asText(epicKey),
                fields.path("project").path("key").asText(properties.getProjectKey()),
                fields.path("summary").asText(),
                documents.toPlainText(fields.path("description")),
                fields.path("status").path("name").asText(),
                labels
        );
    }

    @Override
    public List<JiraIssueSnapshot> findTrackedIssues(int limit) {
        requireConfigured();
        String jql = "project = \"%s\" AND issuetype in (\"%s\", \"%s\") ORDER BY updated DESC"
                .formatted(properties.getProjectKey(), properties.getStoryIssueType(), properties.getTaskIssueType());
        JsonNode response = search(jql, Math.max(1, limit),
                List.of("summary", "status", "issuetype", "parent", "labels", "updated", "assignee"));
        List<JiraIssueSnapshot> issues = new ArrayList<>();
        response.path("issues").forEach(issue -> {
            JsonNode fields = issue.path("fields");
            Set<String> labels = new LinkedHashSet<>();
            fields.path("labels").forEach(label -> labels.add(label.asText()));
            issues.add(new JiraIssueSnapshot(
                    issue.path("key").asText(),
                    fields.path("issuetype").path("name").asText(),
                    fields.path("summary").asText(),
                    fields.path("status").path("name").asText(),
                    fields.path("status").path("statusCategory").path("key").asText(),
                    fields.path("parent").path("key").asText(),
                    fields.path("assignee").path("displayName").asText(),
                    parseInstant(fields.path("updated").asText()),
                    labels
            ));
        });
        return issues;
    }

    @Override
    public Optional<String> findGeneratedIssue(String epicKey, String kind, int sequence) {
        String jql = "project = \"%s\" AND labels = \"%s\" AND labels = \"%s\"".formatted(
                properties.getProjectKey(), sourceLabel(epicKey), kindLabel(kind, sequence));
        JsonNode response = search(jql, 1, List.of("key"));
        JsonNode issues = response.path("issues");
        if (issues.isArray() && !issues.isEmpty()) {
            return Optional.ofNullable(issues.get(0).path("key").asText(null));
        }
        return Optional.empty();
    }

    @Override
    public String createStory(EpicRequirement epic, StoryPlan story, int sequence) {
        Map<String, Object> fields = baseFields(
                epic,
                story.summary(),
                properties.getStoryIssueType(),
                documents.storyDescription(story),
                List.of("ai-generated", sourceLabel(epic.key()), kindLabel("story", sequence))
        );
        return createIssue(fields);
    }

    @Override
    public String createTask(EpicRequirement epic, String storyKey, StoryPlan story, int sequence, TaskKind kind) {
        String summary = "[AI][%s] %s".formatted(kind.displayName(), story.summary());
        Map<String, Object> fields = baseFields(
                epic,
                summary,
                properties.getTaskIssueType(),
                documents.taskDescription(storyKey, story, kind),
                List.of("ai-generated", sourceLabel(epic.key()), kindLabel(kind.label(), sequence))
        );
        return createIssue(fields);
    }

    @Override
    public void markPlanned(EpicRequirement epic, int storyCount, int taskCount) {
        addLabel(epic.key(), "ai-planned");
        addComment(epic.key(), "AI planning completed. Created or reused %d stories and %d delivery tasks."
                .formatted(storyCount, taskCount));
    }

    @Override
    public void escalateForHumanReview(EpicRequirement epic, int attempts, List<String> reasons) {
        String explanation = reasons == null || reasons.isEmpty() ? "Unknown planning validation error" : String.join("; ", reasons);
        addComment(epic.key(), "AI planning stopped after %d attempt(s). Human gatekeeper must reverify the requirement. Reasons: %s"
                .formatted(attempts, explanation));
        assignGatekeeper(epic.key());
        transitionTo(epic.key(), properties.getHumanReviewStatus());
    }

    @Override
    public void addAutomationComment(String issueKey, String comment) {
        addComment(issueKey, comment);
    }

    @Override
    public void requestAutomationHumanReview(String issueKey, String handoffReport) {
        addComment(issueKey, handoffReport);
        assignGatekeeper(issueKey);
        transitionTo(issueKey, properties.getHumanReviewStatus());
    }

    @Override
    public List<DevelopmentWorkItem> findPendingDevelopmentWork(String epicKey) {
        String source = sourceLabel(epicKey);
        List<JiraIssueSnapshot> issues = findTrackedIssues(200).stream()
                .filter(issue -> issue.labels().stream().anyMatch(source::equalsIgnoreCase))
                .toList();
        Map<Integer, JiraIssueSnapshot> stories = bySequence(issues, "story");
        Map<Integer, JiraIssueSnapshot> developmentTasks = bySequence(issues, "dev");
        Map<Integer, JiraIssueSnapshot> testingTasks = bySequence(issues, "test");
        return stories.entrySet().stream()
                .filter(entry -> isDevelopmentCandidate(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int sequence = entry.getKey();
                    JiraIssueSnapshot story = entry.getValue();
                    JiraIssueSnapshot development = developmentTasks.get(sequence);
                    JiraIssueSnapshot testing = testingTasks.get(sequence);
                    if (development == null || testing == null) {
                        throw new IllegalStateException("Story " + story.key() + " is missing its generated development or testing task");
                    }
                    return new DevelopmentWorkItem(sequence, story.key(), story.summary(), issueDescription(story.key()),
                            development.key(), issueDescription(development.key()), testing.key());
                }).toList();
    }

    @Override
    public void startDevelopmentWork(DevelopmentWorkItem work) {
        transitionToIfNeeded(work.storyKey(), properties.getDevelopmentStatus());
        transitionToIfNeeded(work.developmentTaskKey(), properties.getDevelopmentStatus());
        String comment = "AI development started for Story %s and Development Task %s."
                .formatted(work.storyKey(), work.developmentTaskKey());
        addComment(work.storyKey(), comment);
        addComment(work.developmentTaskKey(), comment);
    }

    @Override
    public void completeDevelopmentWork(DevelopmentWorkItem work, String commitSha) {
        transitionToIfNeeded(work.developmentTaskKey(), properties.getCompletedStatus());
        transitionToIfNeeded(work.storyKey(), properties.getTestingStatus());
        transitionToIfNeeded(work.testingTaskKey(), properties.getTestingStatus());
        String comment = "AI development completed. Story: %s. Development Task: %s. Commit: %s. Next phase: Testing."
                .formatted(work.storyKey(), work.developmentTaskKey(), commitSha);
        addComment(work.storyKey(), comment);
        addComment(work.developmentTaskKey(), comment);
        addComment(work.testingTaskKey(), comment);
    }

    @Override
    public void failDevelopmentWork(DevelopmentWorkItem work, String failureReport) {
        transitionToIfNeeded(work.storyKey(), properties.getHumanReviewStatus());
        transitionToIfNeeded(work.developmentTaskKey(), properties.getHumanReviewStatus());
        addComment(work.storyKey(), failureReport);
        addComment(work.developmentTaskKey(), failureReport);
        assignGatekeeper(work.storyKey());
        assignGatekeeper(work.developmentTaskKey());
    }

    @Override
    public void syncAutomationPhase(String epicKey, AutomationPhase phase, String commitSha) {
        String targetStatus = switch (phase) {
            case DEVELOPMENT -> properties.getDevelopmentStatus();
            case TESTING -> properties.getTestingStatus();
            case READY_FOR_STAGING -> properties.getStagingStatus();
            case COMPLETED -> properties.getCompletedStatus();
            case HUMAN_REVIEW -> properties.getHumanReviewStatus();
        };
        if (targetStatus == null || targetStatus.isBlank()) return;
        String sourceLabel = sourceLabel(epicKey);
        List<JiraIssueSnapshot> linkedIssues = findTrackedIssues(200).stream()
                .filter(issue -> epicKey.equalsIgnoreCase(issue.parentKey())
                        || issue.labels().stream().anyMatch(sourceLabel::equalsIgnoreCase))
                .toList();
        LOGGER.info("Jira phase sync epic={} phase={} targetStatus={} matchedIssues={}",
                epicKey, phase, targetStatus, linkedIssues.size());
        int transitioned = 0;
        int alreadyCurrent = 0;
        for (JiraIssueSnapshot issue : linkedIssues) {
            if (transitionToIfNeeded(issue.key(), targetStatus)) {
                transitioned++;
                addComment(issue.key(), "AI delivery phase synchronized to %s%s."
                        .formatted(phase, commitSha == null || commitSha.isBlank() ? "" : " Commit: " + commitSha));
            } else {
                alreadyCurrent++;
            }
        }
        LOGGER.info("Jira phase sync completed epic={} transitioned={} alreadyCurrent={}",
                epicKey, transitioned, alreadyCurrent);
    }

    private JsonNode search(String jql, int maxResults, List<String> fields) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/api/3/search/jql")
                        .queryParam("jql", jql)
                        .queryParam("maxResults", maxResults)
                        .queryParam("fields", String.join(",", fields))
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private Map<Integer, JiraIssueSnapshot> bySequence(List<JiraIssueSnapshot> issues, String kind) {
        Map<Integer, JiraIssueSnapshot> result = new LinkedHashMap<>();
        for (JiraIssueSnapshot issue : issues) {
            for (String label : issue.labels()) {
                Matcher matcher = SEQUENCE_LABEL.matcher(label);
                if (matcher.matches() && kind.equalsIgnoreCase(matcher.group(1))) {
                    result.put(Integer.parseInt(matcher.group(2)), issue);
                }
            }
        }
        return result;
    }

    private boolean isDevelopmentCandidate(JiraIssueSnapshot issue) {
        return "new".equalsIgnoreCase(issue.statusCategory())
                || "to do".equalsIgnoreCase(issue.status())
                || properties.getDevelopmentStatus().equalsIgnoreCase(issue.status());
    }

    private String issueDescription(String issueKey) {
        JsonNode issue = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/rest/api/3/issue/{key}")
                        .queryParam("fields", "description").build(issueKey))
                .retrieve().body(JsonNode.class);
        return documents.toPlainText(issue.path("fields").path("description"));
    }

    private Map<String, Object> baseFields(EpicRequirement epic, String summary, String issueType,
                                           Map<String, Object> description, List<String> labels) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", epic.projectKey()));
        fields.put("summary", truncate(summary, 250));
        fields.put("issuetype", Map.of("name", issueType));
        fields.put("description", description);
        fields.put("labels", labels);
        fields.put("parent", Map.of("key", epic.key()));
        return fields;
    }

    private String createIssue(Map<String, Object> fields) {
        JsonNode response = restClient.post()
                .uri("/rest/api/3/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("fields", fields))
                .retrieve()
                .body(JsonNode.class);
        String key = response.path("key").asText();
        if (key.isBlank()) {
            throw new IllegalStateException("Jira created an issue but returned no issue key");
        }
        return key;
    }

    private void addLabel(String issueKey, String label) {
        restClient.put()
                .uri("/rest/api/3/issue/{key}", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("update", Map.of("labels", List.of(Map.of("add", label)))))
                .retrieve()
                .toBodilessEntity();
    }

    private void addComment(String issueKey, String comment) {
        restClient.post()
                .uri("/rest/api/3/issue/{key}/comment", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", documents.comment(comment)))
                .retrieve()
                .toBodilessEntity();
    }

    private void assignGatekeeper(String issueKey) {
        if (properties.getHumanGatekeeperAccountId() == null || properties.getHumanGatekeeperAccountId().isBlank()) {
            return;
        }
        restClient.put()
                .uri("/rest/api/3/issue/{key}/assignee", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accountId", properties.getHumanGatekeeperAccountId()))
                .retrieve()
                .toBodilessEntity();
    }

    private void transitionTo(String issueKey, String targetStatus) {
        if (targetStatus == null || targetStatus.isBlank()) {
            return;
        }
        JsonNode response = restClient.get()
                .uri("/rest/api/3/issue/{key}/transitions", issueKey)
                .retrieve()
                .body(JsonNode.class);
        String currentStatus = response.path("transitions").isEmpty() ? "" : "";
        transitionToResponse(issueKey, targetStatus, response);
    }

    private boolean transitionToIfNeeded(String issueKey, String targetStatus) {
        JsonNode issue = restClient.get().uri("/rest/api/3/issue/{key}", issueKey)
                .retrieve().body(JsonNode.class);
        if (targetStatus.equalsIgnoreCase(issue.path("fields").path("status").path("name").asText())) return false;
        transitionTo(issueKey, targetStatus);
        return true;
    }

    private void transitionToResponse(String issueKey, String targetStatus, JsonNode response) {
        String transitionId = null;
        for (JsonNode transition : response.path("transitions")) {
            if (targetStatus.equalsIgnoreCase(transition.path("name").asText())
                    || targetStatus.equalsIgnoreCase(transition.path("to").path("name").asText())) {
                transitionId = transition.path("id").asText();
                break;
            }
        }
        if (transitionId == null || transitionId.isBlank()) {
            throw new IllegalStateException("Jira has no available transition to status: " + targetStatus);
        }
        restClient.post()
                .uri("/rest/api/3/issue/{key}/transitions", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .toBodilessEntity();
    }

    private void requireConfigured() {
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.getBaseUrl())) missing.add("JIRA_BASE_URL");
        if (isBlank(properties.getEmail())) missing.add("JIRA_EMAIL");
        if (isBlank(properties.getApiToken())) missing.add("JIRA_API_TOKEN");
        if (isBlank(properties.getProjectKey())) missing.add("JIRA_PROJECT_KEY");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing Jira configuration: " + String.join(", ", missing));
        }
    }

    private String sourceLabel(String epicKey) {
        return "ai-source-" + epicKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }

    private String kindLabel(String kind, int sequence) {
        return "ai-" + kind + "-" + String.format("%02d", sequence);
    }

    private String truncate(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                        .toInstant();
            } catch (DateTimeParseException secondFailure) {
                return null;
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
