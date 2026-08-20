package com.workflowautomation.jiraplanner.automation.project;

import com.workflowautomation.jiraplanner.config.AutomationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ProjectRegistry {

    private final Map<String, AutomationProperties.Project> configuredProjects = new LinkedHashMap<>();

    public ProjectRegistry(AutomationProperties properties) {
        for (AutomationProperties.Project project : properties.getProjects()) {
            if (project.getJiraProjectKey() != null && !project.getJiraProjectKey().isBlank()) {
                configuredProjects.put(normalizeKey(project.getJiraProjectKey()), project);
            }
        }
    }

    public ProjectDefinition require(String jiraProjectKey) {
        AutomationProperties.Project configured = configuredProjects.get(normalizeKey(jiraProjectKey));
        if (configured == null) {
            throw new IllegalArgumentException("No automation project is configured for Jira key: " + jiraProjectKey);
        }
        if (isBlank(configured.getRepositoryRoot()) || isBlank(configured.getAllowedDirectory())) {
            throw new IllegalArgumentException("Automation project " + jiraProjectKey
                    + " requires repository-root and allowed-directory");
        }

        Path repositoryRoot = Path.of(configured.getRepositoryRoot()).toAbsolutePath().normalize();
        Path allowedDirectory = repositoryRoot.resolve(configured.getAllowedDirectory()).normalize();
        if (!allowedDirectory.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Allowed project directory must remain inside the configured repository");
        }
        if (!Files.isDirectory(repositoryRoot.resolve(".git"))) {
            throw new IllegalArgumentException("Configured repository root is not a Git repository: " + repositoryRoot);
        }
        if (!Files.isDirectory(allowedDirectory)) {
            throw new IllegalArgumentException("Configured allowed project directory does not exist: " + allowedDirectory);
        }

        return new ProjectDefinition(
                normalizeKey(configured.getJiraProjectKey()),
                repositoryRoot,
                allowedDirectory,
                defaultIfBlank(configured.getBaseBranch(), "main"),
                defaultIfBlank(configured.getRemote(), "origin"),
                defaultIfBlank(configured.getBranchPrefix(), "ai/epic-"),
                configured.getStagingJenkinsJob() == null ? "" : configured.getStagingJenkinsJob().trim(),
                configured.getDevelopmentCommand(), configured.getStagingDeployCommand(), configured.getStagingHealthUrl()
        );
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
