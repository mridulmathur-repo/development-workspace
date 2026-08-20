package com.workflowautomation.jiraplanner.planning;

import java.util.List;

public record StoryPlan(
        String summary,
        String description,
        List<AcceptanceCriterion> acceptanceCriteria
) {
}
