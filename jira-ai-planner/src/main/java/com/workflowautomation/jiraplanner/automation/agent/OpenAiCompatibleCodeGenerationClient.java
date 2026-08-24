package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleCodeGenerationClient implements AiCodeGenerationClient {
    private static final String SYSTEM_PROMPT = """
            You are a senior Java Spring Boot and React developer. Implement exactly one Jira Story.
            Return one valid unified Git patch and a concise summary. Patch paths must be repository-relative,
            must start with DemoMart/, and must never touch secrets, .env, .git, target, node_modules, generated
            artifacts, or any other project. Do not return shell commands or markdown fences. Preserve existing
            architecture and add or update tests where appropriate. The patch must apply with git apply.
            """;

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleCodeGenerationClient(AiProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedCodeChange generatePatch(AgentAssignment assignment, String codeContext) {
        requireConfigured();
        String prompt = """
                Epic: %s - %s
                Story: %s - %s
                Story description and acceptance criteria:
                %s

                Development Task: %s
                Task instructions:
                %s

                Human retry instructions:
                %s

                Relevant existing DemoMart source files:
                %s
                """.formatted(assignment.epicKey(), assignment.epicSummary(), assignment.storyKey(),
                assignment.storySummary(), assignment.storyDescription(), assignment.developmentTaskKey(),
                assignment.developmentTaskDescription(), blankFallback(assignment.humanInstructions()), codeContext);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(), "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxOutputTokens(),
                "messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)),
                "response_format", responseFormat());
        JsonNode response = restClient.post().uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        try {
            String content = response.path("choices").path(0).path("message").path("content").asText();
            GeneratedCodeChange change = objectMapper.readValue(content, GeneratedCodeChange.class);
            validate(change);
            return change;
        } catch (Exception exception) {
            throw new IllegalStateException("AI provider returned an invalid code-change response", exception);
        }
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("summary", "patch"),
                "properties", Map.of("summary", Map.of("type", "string"), "patch", Map.of("type", "string")));
        return Map.of("type", "json_schema",
                "json_schema", Map.of("name", "demomart_code_change", "strict", true, "schema", schema));
    }

    private void validate(GeneratedCodeChange change) {
        if (change.patch().isBlank() || !change.patch().contains("diff --git a/DemoMart/"))
            throw new IllegalArgumentException("Generated response contains no DemoMart unified patch");
        String normalized = change.patch().replace('\\', '/');
        if (normalized.contains("../")) throw new IllegalArgumentException("Generated patch contains path traversal");
        for (String line : normalized.split("\\R")) {
            if (line.startsWith("diff --git ")
                    && !line.matches("diff --git a/DemoMart/\\S+ b/DemoMart/\\S+"))
                throw new IllegalArgumentException("Generated patch attempts to leave DemoMart: " + line);
        }
        String lower = normalized.toLowerCase();
        if (lower.contains("/.env") || lower.contains("/.git/") || lower.contains("/node_modules/") || lower.contains("/target/"))
            throw new IllegalArgumentException("Generated patch targets a protected file or build directory");
    }

    private void requireConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new IllegalStateException("AI_API_KEY is required for autonomous development");
    }
    private String blankFallback(String value) { return value == null || value.isBlank() ? "None" : value; }
}
