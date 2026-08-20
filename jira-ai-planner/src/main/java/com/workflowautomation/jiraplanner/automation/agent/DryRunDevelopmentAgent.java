package com.workflowautomation.jiraplanner.automation.agent;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Safe Phase 3 starter worker. It validates the assignment boundary but does not call a model,
 * edit files, or push code. A real model-backed implementation can replace this component later.
 */
@Component
@ConditionalOnProperty(name = "automation.worker-mode", havingValue = "dry-run", matchIfMissing = true)
public class DryRunDevelopmentAgent implements DevelopmentAgent {

    @Override
    public AgentOutcome execute(AgentAssignment assignment) {
        if (assignment == null || assignment.allowedDirectory() == null
                || assignment.allowedDirectory().isBlank()) {
            throw new IllegalArgumentException("Development assignment must include an allowed directory");
        }
        return AgentOutcome.success(assignment.branchHeadCommit());
    }
}
