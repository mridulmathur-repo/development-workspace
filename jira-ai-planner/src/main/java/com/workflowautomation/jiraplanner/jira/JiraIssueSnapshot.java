package com.workflowautomation.jiraplanner.jira;

import java.time.Instant;
import java.util.Set;

public record JiraIssueSnapshot(
        String key,
        String issueType,
        String summary,
        String status,
        String statusCategory,
        String parentKey,
        String assignee,
        Instant updatedAt,
        Set<String> labels
) {
}
