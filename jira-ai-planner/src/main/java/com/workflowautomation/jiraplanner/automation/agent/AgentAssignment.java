package com.workflowautomation.jiraplanner.automation.agent;

public record AgentAssignment(
        String executionId,
        String epicKey,
        String epicSummary,
        String branchName,
        String branchHeadCommit,
        String allowedDirectory,
        String humanInstructions
) {
}
