package com.workflowautomation.jiraplanner.automation.execution;

public class ExecutionNotFoundException extends RuntimeException {
    public ExecutionNotFoundException(String id) {
        super("Automation execution was not found: " + id);
    }
}
