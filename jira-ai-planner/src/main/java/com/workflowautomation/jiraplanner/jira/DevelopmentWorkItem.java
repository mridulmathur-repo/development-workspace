package com.workflowautomation.jiraplanner.jira;

/** One independently executable Story and its generated delivery tasks. */
public record DevelopmentWorkItem(
        int sequence,
        String storyKey,
        String storySummary,
        String storyDescription,
        String developmentTaskKey,
        String developmentTaskDescription,
        String testingTaskKey
) {
}
