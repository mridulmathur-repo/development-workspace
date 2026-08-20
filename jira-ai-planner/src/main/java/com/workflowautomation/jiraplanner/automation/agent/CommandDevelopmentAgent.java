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
    private final Duration timeout = Duration.ofMinutes(15);

    public CommandDevelopmentAgent(ProjectRegistry projects) { this.projects = projects; }

    @Override
    public AgentOutcome execute(AgentAssignment assignment) {
        try {
            requireWindows();
            String projectKey = projectKey(assignment.epicKey());
            ProjectDefinition project = projects.require(projectKey);
            if (blank(project.developmentCommand())) return failure("AUTOMATION_DEVELOPMENT_COMMAND is not configured");
            run(project.repositoryRoot(), "git checkout " + assignment.branchName());
            run(project.repositoryRoot(), "git pull --ff-only " + project.remote() + " " + assignment.branchName());
            run(project.allowedDirectory(), project.developmentCommand());
            String projectPath = project.repositoryRoot().relativize(project.allowedDirectory()).toString();
            String changed = run(project.repositoryRoot(), "git status --porcelain -- \"" + projectPath + "\"");
            if (changed.isBlank()) return failure("Development command produced no committed code changes");
            run(project.repositoryRoot(), "git add -- \"" + projectPath + "\"");
            run(project.repositoryRoot(), "git commit -m \"AI development: " + assignment.epicKey() + "\"");
            run(project.repositoryRoot(), "git push " + project.remote() + " HEAD:" + assignment.branchName());
            String sha = run(project.repositoryRoot(), "git rev-parse HEAD").trim();
            if (!blank(project.stagingDeployCommand())) run(project.allowedDirectory(), project.stagingDeployCommand());
            if (!blank(project.stagingHealthUrl())) verifyHealth(project.stagingHealthUrl());
            return AgentOutcome.success(sha);
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "Development worker failed" : ex.getMessage());
        }
    }

    private void requireWindows() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new IllegalStateException("Command worker requires the planner to run natively on Windows; Linux Docker mode supports dry-run only");
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
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", command)
                .directory(directory.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Command timed out: " + command); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("Command failed (" + process.exitValue() + "): " + output.substring(0, Math.min(1200, output.length())));
        return output;
    }
    private AgentOutcome failure(String message) { return new AgentOutcome(false, "", new FailureReport(FailureCategory.DEVELOPMENT, message, "Code changed, pushed and staging verified", message, "", "Inspect worker output and retry", "high")); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
