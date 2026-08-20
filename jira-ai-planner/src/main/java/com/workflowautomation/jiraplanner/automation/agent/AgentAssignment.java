package com.workflowautomation.jiraplanner.automation.agent;

public record AgentAssignment(
        String executionId,
        String epicKey,
        String epicSummary,
        String branchName,
        String branchHeadCommit,
        String allowedDirectory,
        String humanInstructions,
        String storyKey,
        String storySummary,
        String storyDescription,
        String developmentTaskKey,
        String developmentTaskDescription
) {
    public AgentAssignment(String executionId, String epicKey, String epicSummary, String branchName,
                           String branchHeadCommit, String allowedDirectory, String humanInstructions) {
        this(executionId, epicKey, epicSummary, branchName, branchHeadCommit, allowedDirectory,
                humanInstructions, "", "", "", "", "");
    }
}
