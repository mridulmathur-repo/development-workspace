package com.workflowautomation.jiraplanner.automation.project;

import java.nio.file.Path;

public record ProjectDefinition(
        String jiraProjectKey,
        Path repositoryRoot,
        Path allowedDirectory,
        String baseBranch,
        String remote,
        String branchPrefix,
        String stagingJenkinsJob,
        String developmentCommand,
        String stagingDeployCommand,
        String stagingHealthUrl
) {
    public ProjectDefinition(String jiraProjectKey, Path repositoryRoot, Path allowedDirectory,
                             String baseBranch, String remote, String branchPrefix, String stagingJenkinsJob) {
        this(jiraProjectKey, repositoryRoot, allowedDirectory, baseBranch, remote, branchPrefix,
                stagingJenkinsJob, "", "", "");
    }
}
