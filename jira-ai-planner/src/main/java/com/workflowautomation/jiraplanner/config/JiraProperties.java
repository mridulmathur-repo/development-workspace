package com.workflowautomation.jiraplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jira")
public class JiraProperties {

    private String baseUrl = "";
    private String email = "";
    private String apiToken = "";
    private String projectKey = "";
    private String readyStatus = "Ready for AI Planning";
    private String humanReviewStatus = "Human Review Required";
    private String humanGatekeeperAccountId = "";
    private String epicIssueType = "Epic";
    private String storyIssueType = "Story";
    private String taskIssueType = "Task";
    private String developmentStatus = "In Development";
    private String testingStatus = "In Testing";
    private String stagingStatus = "Ready for Staging";
    private String completedStatus = "Done";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getReadyStatus() {
        return readyStatus;
    }

    public void setReadyStatus(String readyStatus) {
        this.readyStatus = readyStatus;
    }

    public String getHumanReviewStatus() {
        return humanReviewStatus;
    }

    public void setHumanReviewStatus(String humanReviewStatus) {
        this.humanReviewStatus = humanReviewStatus;
    }

    public String getHumanGatekeeperAccountId() {
        return humanGatekeeperAccountId;
    }

    public void setHumanGatekeeperAccountId(String humanGatekeeperAccountId) {
        this.humanGatekeeperAccountId = humanGatekeeperAccountId;
    }

    public String getEpicIssueType() {
        return epicIssueType;
    }

    public void setEpicIssueType(String epicIssueType) {
        this.epicIssueType = epicIssueType;
    }

    public String getStoryIssueType() {
        return storyIssueType;
    }

    public void setStoryIssueType(String storyIssueType) {
        this.storyIssueType = storyIssueType;
    }

    public String getTaskIssueType() {
        return taskIssueType;
    }

    public void setTaskIssueType(String taskIssueType) {
        this.taskIssueType = taskIssueType;
    }

    public String getDevelopmentStatus() { return developmentStatus; }
    public void setDevelopmentStatus(String developmentStatus) { this.developmentStatus = developmentStatus; }
    public String getTestingStatus() { return testingStatus; }
    public void setTestingStatus(String testingStatus) { this.testingStatus = testingStatus; }
    public String getStagingStatus() { return stagingStatus; }
    public void setStagingStatus(String stagingStatus) { this.stagingStatus = stagingStatus; }
    public String getCompletedStatus() { return completedStatus; }
    public void setCompletedStatus(String completedStatus) { this.completedStatus = completedStatus; }
}
