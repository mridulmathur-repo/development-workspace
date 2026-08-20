package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.automation.execution.FailureReport;

public record AgentOutcome(boolean successful, String commitSha, FailureReport failure) {

    public static AgentOutcome success(String commitSha) {
        return new AgentOutcome(true, commitSha, null);
    }
}
