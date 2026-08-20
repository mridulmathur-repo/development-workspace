package com.workflowautomation.jiraplanner.planning;

public record PlanningResult(
        String epicKey,
        Status status,
        int storiesCreated,
        int tasksCreated,
        int generationAttempts,
        String message
) {
    public enum Status {
        COMPLETED,
        ALREADY_PLANNED,
        HUMAN_REVIEW_REQUIRED
    }
}
