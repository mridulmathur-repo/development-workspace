package com.workflowautomation.jiraplanner.dashboard;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecutionRepository;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.config.AiProperties;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import com.workflowautomation.jiraplanner.config.DashboardProperties;
import com.workflowautomation.jiraplanner.config.JiraProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import com.workflowautomation.jiraplanner.jira.JiraIssueSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardService {

    private final JiraGateway jira;
    private final AutomationExecutionRepository executions;
    private final JiraProperties jiraProperties;
    private final AiProperties aiProperties;
    private final AutomationProperties automationProperties;
    private final DashboardProperties dashboardProperties;

    public DashboardService(JiraGateway jira, AutomationExecutionRepository executions,
                            JiraProperties jiraProperties, AiProperties aiProperties,
                            AutomationProperties automationProperties, DashboardProperties dashboardProperties) {
        this.jira = jira;
        this.executions = executions;
        this.jiraProperties = jiraProperties;
        this.aiProperties = aiProperties;
        this.automationProperties = automationProperties;
        this.dashboardProperties = dashboardProperties;
    }

    public DashboardSnapshot snapshot() {
        Instant now = Instant.now();
        List<IntegrationStatus> integrations = new ArrayList<>();
        List<DashboardIssue> issues;
        try {
            issues = jira.findTrackedIssues(Math.max(1, dashboardProperties.getMaxIssues())).stream()
                    .map(issue -> toDashboardIssue(issue, now))
                    .toList();
            integrations.add(new IntegrationStatus("Jira", "CONNECTED",
                    "Loaded " + issues.size() + " Stories and Tasks"));
        } catch (RuntimeException exception) {
            issues = List.of();
            integrations.add(new IntegrationStatus("Jira", "ERROR", safeMessage(exception)));
        }

        List<AutomationExecutionView> executionViews = executions.findAllByOrderByUpdatedAtDesc().stream()
                .map(AutomationExecutionView::from)
                .toList();

        integrations.add(new IntegrationStatus("AI provider", configured(aiProperties.getApiKey()),
                configuredDetail(aiProperties.getApiKey(), aiProperties.getModel())));
        integrations.add(new IntegrationStatus("GitHub", automationProperties.getProjects().isEmpty()
                ? "MISSING" : "CONFIGURED", automationProperties.getProjects().isEmpty()
                ? "No project mapping" : automationProperties.getProjects().size() + " project mapping(s)"));
        boolean stagingConfigured = automationProperties.getProjects().stream()
                .anyMatch(project -> project.getStagingJenkinsJob() != null
                        && !project.getStagingJenkinsJob().isBlank());
        integrations.add(new IntegrationStatus("Staging", stagingConfigured ? "CONFIGURED" : "MISSING",
                stagingConfigured ? "Jenkins job mapped" : "No staging job mapped"));
        integrations.add(new IntegrationStatus("Execution database", "CONNECTED",
                executionViews.size() + " execution record(s)"));

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("stories", countType(issues, jiraProperties.getStoryIssueType()));
        counts.put("tasks", countType(issues, jiraProperties.getTaskIssueType()));
        counts.put("stuck", issues.stream().filter(DashboardIssue::stuck).count());
        counts.put("activeExecutions", executionViews.stream()
                .filter(execution -> "ACTIVE".equals(execution.status().name())).count());
        counts.put("humanReview", executionViews.stream()
                .filter(execution -> "HUMAN_REVIEW_REQUIRED".equals(execution.status().name())).count());

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (String stage : List.of("READY", "DEVELOPMENT", "TESTING", "STAGING", "HUMAN_REVIEW", "COMPLETED")) {
            stageCounts.put(stage, issues.stream().filter(issue -> stage.equals(issue.stage())).count());
        }

        return new DashboardSnapshot(now, Math.max(1, dashboardProperties.getStuckAfterMinutes()),
                jiraProperties.getProjectKey(), jiraProperties.getBaseUrl(), counts, stageCounts,
                issues, executionViews, integrations);
    }

    private DashboardIssue toDashboardIssue(JiraIssueSnapshot issue, Instant now) {
        long idleMinutes = issue.updatedAt() == null ? 0
                : Math.max(0, Duration.between(issue.updatedAt(), now).toMinutes());
        String stage = stage(issue);
        boolean humanReview = "HUMAN_REVIEW".equals(stage);
        boolean stuck = !"COMPLETED".equals(stage)
                && (humanReview || idleMinutes >= Math.max(1, dashboardProperties.getStuckAfterMinutes()));
        String reason = humanReview ? "Waiting for human gatekeeper"
                : stuck ? "No Jira update for " + idleMinutes + " minutes" : "";
        return new DashboardIssue(issue.key(), issue.issueType(), issue.summary(), issue.status(), stage,
                issue.parentKey(), issue.assignee(), issue.updatedAt(), idleMinutes, stuck, reason, issue.labels());
    }

    private String stage(JiraIssueSnapshot issue) {
        String status = lower(issue.status());
        String category = lower(issue.statusCategory());
        if ("done".equals(category)) return "COMPLETED";
        if (status.contains("human") || status.contains("review") || status.contains("blocked")) {
            return "HUMAN_REVIEW";
        }
        if (status.contains("deploy") || status.contains("staging")) return "STAGING";
        if (status.contains("test") || (isInProgress(category, status) && isTestingTask(issue))) return "TESTING";
        if (status.contains("develop") || isInProgress(category, status)) return "DEVELOPMENT";
        return "READY";
    }

    private boolean isInProgress(String category, String status) {
        return "indeterminate".equals(category) || status.contains("progress");
    }

    private boolean isTestingTask(JiraIssueSnapshot issue) {
        String summary = lower(issue.summary());
        return summary.contains("[testing]") || summary.contains("test")
                || issue.labels().stream().anyMatch(label -> lower(label).matches("ai-test-.*"));
    }

    private long countType(List<DashboardIssue> issues, String type) {
        return issues.stream().filter(issue -> type.equalsIgnoreCase(issue.issueType())).count();
    }

    private String configured(String value) {
        return value == null || value.isBlank() ? "MISSING" : "CONFIGURED";
    }

    private String configuredDetail(String value, String detail) {
        return value == null || value.isBlank() ? "API key is not configured" : detail;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(180, message.length()));
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
