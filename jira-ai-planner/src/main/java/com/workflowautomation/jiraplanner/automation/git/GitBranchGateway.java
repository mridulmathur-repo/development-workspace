package com.workflowautomation.jiraplanner.automation.git;

import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;

public interface GitBranchGateway {

    BranchProvisionResult ensureRemoteEpicBranch(ProjectDefinition project, String branchName);

    String remoteBranchHead(ProjectDefinition project, String branchName);
}
