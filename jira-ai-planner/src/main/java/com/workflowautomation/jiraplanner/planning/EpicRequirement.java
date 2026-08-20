package com.workflowautomation.jiraplanner.planning;

import java.util.Set;

public record EpicRequirement(
        String key,
        String projectKey,
        String summary,
        String description,
        String status,
        Set<String> labels
) {
    public boolean alreadyPlanned() {
        return labels != null && labels.contains("ai-planned");
    }
}
