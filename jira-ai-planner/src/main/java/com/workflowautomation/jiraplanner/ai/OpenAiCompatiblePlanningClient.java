package com.workflowautomation.jiraplanner.ai;

import com.workflowautomation.jiraplanner.config.AiProperties;
import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import com.workflowautomation.jiraplanner.planning.PlanningPlan;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatiblePlanningClient implements AiPlanningClient {

    private static final String SYSTEM_PROMPT = """
            You are a senior technical product manager. Turn one Jira Epic into small, independently testable user stories.
            Every story must contain a concise summary, a business-focused description, and testable acceptance criteria.
            Every acceptance criterion must use GIVEN, WHEN and THEN fields. Do not generate development tasks,
            testing tasks, non-functional requirements or implementation details. Return only the requested JSON schema.
            """;

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatiblePlanningClient(AiProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanningPlan generatePlan(EpicRequirement epic, List<String> previousViolations) {
        requireConfigured();
        String retryInstruction = previousViolations == null || previousViolations.isEmpty()
                ? ""
                : "\nYour previous response was rejected for these reasons. Correct every item:\n- "
                + String.join("\n- ", previousViolations);
        String userPrompt = """
                Epic key: %s
                Epic summary: %s
                Epic description:
                %s
                %s
                """.formatted(epic.key(), epic.summary(), epic.description(), retryInstruction);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxOutputTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", responseFormat()
        );

        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        try {
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("AI provider returned no message content");
            }
            return objectMapper.readValue(content.asText(), PlanningPlan.class);
        } catch (Exception exception) {
            throw new IllegalStateException("AI provider returned an unreadable planning response", exception);
        }
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> criterion = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("given", "when", "then"),
                "properties", Map.of(
                        "given", Map.of("type", "string"),
                        "when", Map.of("type", "string"),
                        "then", Map.of("type", "string")
                )
        );
        Map<String, Object> story = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("summary", "description", "acceptanceCriteria"),
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "acceptanceCriteria", Map.of("type", "array", "minItems", 1, "items", criterion)
                )
        );
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("stories"),
                "properties", Map.of(
                        "stories", Map.of("type", "array", "minItems", 1, "maxItems", 10, "items", story)
                )
        );
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "jira_epic_plan", "strict", true, "schema", schema)
        );
    }

    private void requireConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("AI_API_KEY is required before planning an Epic");
        }
    }
}
