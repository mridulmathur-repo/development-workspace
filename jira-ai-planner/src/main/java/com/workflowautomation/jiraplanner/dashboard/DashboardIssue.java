package com.workflowautomation.jiraplanner.dashboard;

import java.time.Instant;
import java.util.Set;

public record DashboardIssue(
        String key,
        String issueType,
        String summary,
        String status,
        String stage,
        String parentKey,
        String assignee,
        Instant updatedAt,
        long idleMinutes,
        boolean stuck,
        String stuckReason,
        Set<String> labels
) {
}
