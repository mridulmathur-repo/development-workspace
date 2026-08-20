package com.workflowautomation.jiraplanner.automation.git;

import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessGitBranchGateway implements GitBranchGateway {

    private final Duration timeout;

    public ProcessGitBranchGateway(AutomationProperties properties) {
        this.timeout = Duration.ofSeconds(Math.max(10, properties.getGitCommandTimeoutSeconds()));
    }

    @Override
    public BranchProvisionResult ensureRemoteEpicBranch(ProjectDefinition project, String branchName) {
        validateRemote(project.remote());
        validateRef(project.baseBranch(), "base branch");
        validateRef(branchName, "Epic branch");
        run(project, "check-ref-format", "--branch", branchName);

        String existingSha = remoteBranchSha(project, branchName);
        if (!existingSha.isBlank()) {
            return new BranchProvisionResult(branchName, existingSha, false);
        }

        String fetchSpec = "+refs/heads/%s:refs/remotes/%s/%s"
                .formatted(project.baseBranch(), project.remote(), project.baseBranch());
        run(project, "fetch", "--no-tags", project.remote(), fetchSpec);
        String baseSha = run(project, "rev-parse",
                "refs/remotes/%s/%s".formatted(project.remote(), project.baseBranch())).trim();
        if (!baseSha.matches("[0-9a-fA-F]{40,64}")) {
            throw new IllegalStateException("Git returned an invalid base commit for " + project.baseBranch());
        }

        run(project, "push", project.remote(), baseSha + ":refs/heads/" + branchName);
        String createdSha = remoteBranchSha(project, branchName);
        if (createdSha.isBlank()) {
            throw new IllegalStateException("Git push completed but the remote Epic branch cannot be found");
        }
        return new BranchProvisionResult(branchName, createdSha, true);
    }

    @Override
    public String remoteBranchHead(ProjectDefinition project, String branchName) {
        validateRemote(project.remote());
        validateRef(branchName, "Epic branch");
        String sha = remoteBranchSha(project, branchName);
        if (sha.isBlank()) {
            throw new IllegalStateException("Remote Epic branch does not exist: " + branchName);
        }
        return sha;
    }

    private String remoteBranchSha(ProjectDefinition project, String branchName) {
        String output = run(project, "ls-remote", "--heads", project.remote(), "refs/heads/" + branchName);
        if (output.isBlank()) {
            return "";
        }
        String[] columns = output.trim().split("\\s+");
        return columns.length == 0 ? "" : columns[0];
    }

    private String run(ProjectDefinition project, String... gitArguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(project.repositoryRoot().toString());
        command.addAll(List.of(gitArguments));

        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("Git command timed out after " + timeout.toSeconds() + " seconds");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Git command failed: " + safeOutput(output));
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start Git. Verify that Git is installed for the planner process.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git command was interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String safeOutput(String output) {
        if (output == null || output.isBlank()) {
            return "no diagnostic output";
        }
        return output.length() <= 1000 ? output : output.substring(0, 1000);
    }

    private void validateRemote(String remote) {
        if (remote == null || !remote.matches("[A-Za-z0-9._-]+") || remote.startsWith("-")) {
            throw new IllegalArgumentException("Configured Git remote name is invalid");
        }
    }

    private void validateRef(String ref, String label) {
        if (ref == null || ref.isBlank() || ref.startsWith("-") || ref.contains("..")
                || ref.contains("@{") || ref.contains("\\") || ref.endsWith(".lock")
                || !ref.matches("[A-Za-z0-9._/-]+")) {
            throw new IllegalArgumentException(label + " is not a safe Git ref name");
        }
    }
}
