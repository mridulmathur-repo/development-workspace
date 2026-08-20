package com.workflowautomation.jiraplanner.automation.agent;

/**
 * Development workers implement this port. They may write production and test code only inside
 * the assignment's allowed directory and must return the pushed Epic-branch commit.
 */
public interface DevelopmentAgent {

    AgentOutcome execute(AgentAssignment assignment);
}
