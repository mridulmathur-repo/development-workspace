package com.workflowautomation.jiraplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("automation")
public class AutomationProperties {

    private boolean enabled;
    private String controlToken = "";
    private int maxAutomaticFixAttempts = 1;
    private long gitCommandTimeoutSeconds = 60;
    private boolean developmentPollerEnabled = true;
    private long developmentPollDelayMilliseconds = 300000;
    private List<Project> projects = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getControlToken() {
        return controlToken;
    }

    public void setControlToken(String controlToken) {
        this.controlToken = controlToken;
    }

    public int getMaxAutomaticFixAttempts() {
        return maxAutomaticFixAttempts;
    }

    public void setMaxAutomaticFixAttempts(int maxAutomaticFixAttempts) {
        this.maxAutomaticFixAttempts = maxAutomaticFixAttempts;
    }

    public long getGitCommandTimeoutSeconds() {
        return gitCommandTimeoutSeconds;
    }

    public void setGitCommandTimeoutSeconds(long gitCommandTimeoutSeconds) {
        this.gitCommandTimeoutSeconds = gitCommandTimeoutSeconds;
    }

    public boolean isDevelopmentPollerEnabled() { return developmentPollerEnabled; }
    public void setDevelopmentPollerEnabled(boolean value) { this.developmentPollerEnabled = value; }
    public long getDevelopmentPollDelayMilliseconds() { return developmentPollDelayMilliseconds; }
    public void setDevelopmentPollDelayMilliseconds(long value) { this.developmentPollDelayMilliseconds = value; }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects == null ? new ArrayList<>() : projects;
    }

    public static class Project {
        private String jiraProjectKey = "";
        private String repositoryRoot = "";
        private String allowedDirectory = "";
        private String baseBranch = "main";
        private String remote = "origin";
        private String branchPrefix = "ai/epic-";
        private String stagingJenkinsJob = "";
        private String developmentCommand = "";
        private String stagingDeployCommand = "";
        private String stagingHealthUrl = "";

        public String getJiraProjectKey() {
            return jiraProjectKey;
        }

        public void setJiraProjectKey(String jiraProjectKey) {
            this.jiraProjectKey = jiraProjectKey;
        }

        public String getRepositoryRoot() {
            return repositoryRoot;
        }

        public void setRepositoryRoot(String repositoryRoot) {
            this.repositoryRoot = repositoryRoot;
        }

        public String getAllowedDirectory() {
            return allowedDirectory;
        }

        public void setAllowedDirectory(String allowedDirectory) {
            this.allowedDirectory = allowedDirectory;
        }

        public String getBaseBranch() {
            return baseBranch;
        }

        public void setBaseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
        }

        public String getRemote() {
            return remote;
        }

        public void setRemote(String remote) {
            this.remote = remote;
        }

        public String getBranchPrefix() {
            return branchPrefix;
        }

        public void setBranchPrefix(String branchPrefix) {
            this.branchPrefix = branchPrefix;
        }

        public String getStagingJenkinsJob() {
            return stagingJenkinsJob;
        }

        public void setStagingJenkinsJob(String stagingJenkinsJob) {
            this.stagingJenkinsJob = stagingJenkinsJob;
        }

        public String getDevelopmentCommand() { return developmentCommand; }
        public void setDevelopmentCommand(String value) { this.developmentCommand = value; }
        public String getStagingDeployCommand() { return stagingDeployCommand; }
        public void setStagingDeployCommand(String value) { this.stagingDeployCommand = value; }
        public String getStagingHealthUrl() { return stagingHealthUrl; }
        public void setStagingHealthUrl(String value) { this.stagingHealthUrl = value; }
    }
}
