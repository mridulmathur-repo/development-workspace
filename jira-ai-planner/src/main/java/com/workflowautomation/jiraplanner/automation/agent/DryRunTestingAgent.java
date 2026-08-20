package com.workflowautomation.jiraplanner.automation.agent;

import org.springframework.stereotype.Component;

/**
 * Safe Phase 3 starter worker. It returns the assigned commit without running commands or
 * changing code; real test execution will be added behind the same contract.
 */
@Component
public class DryRunTestingAgent implements TestingAgent {

    @Override
    public AgentOutcome execute(AgentAssignment assignment) {
        if (assignment == null || assignment.branchHeadCommit() == null
                || assignment.branchHeadCommit().isBlank()) {
            throw new IllegalArgumentException("Testing assignment must include a branch commit");
        }
        return AgentOutcome.success(assignment.branchHeadCommit());
    }
}
