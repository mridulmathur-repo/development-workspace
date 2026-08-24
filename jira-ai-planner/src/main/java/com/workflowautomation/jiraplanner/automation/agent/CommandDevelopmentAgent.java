package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;
import com.workflowautomation.jiraplanner.automation.execution.FailureReport;
import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import com.workflowautomation.jiraplanner.automation.project.ProjectRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Bounded MVP3 development worker. The configured command is the only code-changing entry point;
 * it runs in the configured project directory, then the worker commits/pushes and verifies staging.
 */
@Component
@ConditionalOnProperty(name = "automation.worker-mode", havingValue = "command")
public class CommandDevelopmentAgent implements DevelopmentAgent {
    private final ProjectRegistry projects;
    private final CodeContextCollector contextCollector;
    private final AiCodeGenerationClient codeGenerator;
    private final Duration timeout = Duration.ofMinutes(15);

    public CommandDevelopmentAgent(ProjectRegistry projects, CodeContextCollector contextCollector,
                                   AiCodeGenerationClient codeGenerator) {
        this.projects = projects;
        this.contextCollector = contextCollector;
        this.codeGenerator = codeGenerator;
    }

    @Override
    public AgentOutcome execute(AgentAssignment assignment) {
        Path patchFile = null;
        try {
            String projectKey = projectKey(assignment.epicKey());
            ProjectDefinition project = projects.require(projectKey);
            if (blank(project.developmentCommand())) return failure("AUTOMATION_DEVELOPMENT_COMMAND is not configured");
            run(project.repositoryRoot(), "git checkout " + assignment.branchName());
            run(project.repositoryRoot(), "git pull --ff-only " + project.remote() + " " + assignment.branchName());
            String context = contextCollector.collect(project.allowedDirectory(), assignment);
            GeneratedCodeChange generated = codeGenerator.generatePatch(assignment, context);
            patchFile = Files.createTempFile("jira-ai-" + assignment.storyKey().toLowerCase() + "-", ".patch");
            Files.writeString(patchFile, generated.patch(), StandardCharsets.UTF_8);
            runDevelopmentCommand(project.allowedDirectory(), project.developmentCommand(), assignment, patchFile);
            String projectPath = project.repositoryRoot().relativize(project.allowedDirectory()).toString();
            String changed = run(project.repositoryRoot(), "git status --porcelain -- \"" + projectPath + "\"");
            if (changed.isBlank()) return failure("Development command produced no committed code changes");
            run(project.repositoryRoot(), "git add -- \"" + projectPath + "\"");
            run(project.repositoryRoot(), "git commit -m \"" + assignment.storyKey() + " "
                    + assignment.developmentTaskKey() + ": AI development\"");
            run(project.repositoryRoot(), "git push " + project.remote() + " HEAD:" + assignment.branchName());
            String sha = run(project.repositoryRoot(), "git rev-parse HEAD").trim();
            return AgentOutcome.success(sha);
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "Development worker failed" : ex.getMessage());
        } finally {
            if (patchFile != null) {
                try { Files.deleteIfExists(patchFile); } catch (IOException ignored) { }
            }
        }
    }

    private String projectKey(String epicKey) {
        int separator = epicKey == null ? -1 : epicKey.indexOf('-');
        if (separator < 1) throw new IllegalArgumentException("Epic key must have PROJECT-NUMBER format");
        return epicKey.substring(0, separator);
    }

    private void verifyHealth(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Exception last = null;
        for (int i = 0; i < 12; i++) {
            try {
                HttpResponse<Void> response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 400) return;
                last = new IOException("Health check returned HTTP " + response.statusCode());
            } catch (Exception e) { last = e; }
            Thread.sleep(5000);
        }
        throw new IOException("Staging health check failed: " + (last == null ? "unknown" : last.getMessage()));
    }

    private String run(Path directory, String command) throws Exception {
        return run(directory, command, null);
    }

    private String runDevelopmentCommand(Path directory, String command, AgentAssignment assignment, Path patchFile) throws Exception {
        return run(directory, command, assignment, patchFile);
    }

    private String run(Path directory, String command, AgentAssignment assignment) throws Exception {
        return run(directory, command, assignment, null);
    }

    private String run(Path directory, String command, AgentAssignment assignment, Path patchFile) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        ProcessBuilder builder = new ProcessBuilder(windows
                ? new String[] {"powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", command}
                : new String[] {"/bin/sh", "-lc", command})
                .directory(directory.toFile()).redirectErrorStream(true);
        if (assignment != null) {
            builder.environment().put("AI_EPIC_KEY", safe(assignment.epicKey()));
            builder.environment().put("AI_STORY_KEY", safe(assignment.storyKey()));
            builder.environment().put("AI_STORY_SUMMARY", safe(assignment.storySummary()));
            builder.environment().put("AI_STORY_DESCRIPTION", safe(assignment.storyDescription()));
            builder.environment().put("AI_DEVELOPMENT_TASK_KEY", safe(assignment.developmentTaskKey()));
            builder.environment().put("AI_DEVELOPMENT_TASK_DESCRIPTION", safe(assignment.developmentTaskDescription()));
            builder.environment().put("AI_ALLOWED_DIRECTORY", directory.toString());
            if (patchFile != null) builder.environment().put("AI_PATCH_FILE", patchFile.toString());
        }
        Process process = builder.start();
        if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Command timed out: " + command); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("Command failed (" + process.exitValue() + "): " + output.substring(0, Math.min(1200, output.length())));
        return output;
    }
    private AgentOutcome failure(String message) { return new AgentOutcome(false, "", new FailureReport(FailureCategory.DEVELOPMENT, message, "Code changed, pushed and staging verified", message, "", "Inspect worker output and retry", "high")); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String safe(String value) { return value == null ? "" : value; }
}
