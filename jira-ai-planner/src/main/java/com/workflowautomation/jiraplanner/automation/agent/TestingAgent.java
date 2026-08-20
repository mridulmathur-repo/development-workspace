package com.workflowautomation.jiraplanner.automation.agent;

/**
 * Independent testing workers implement this port. They test the exact assigned commit and do not
 * modify production code; failures return evidence to the orchestrator for controlled correction.
 */
public interface TestingAgent {

    AgentOutcome execute(AgentAssignment assignment);
}
