package com.workflowautomation.jiraplanner.automation.git;

public record BranchProvisionResult(String branchName, String commitSha, boolean created) {
}
