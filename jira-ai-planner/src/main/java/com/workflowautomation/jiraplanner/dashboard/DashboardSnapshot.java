package com.workflowautomation.jiraplanner.dashboard;

import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardSnapshot(
        Instant generatedAt,
        long stuckAfterMinutes,
        String jiraProjectKey,
        String jiraBaseUrl,
        Map<String, Long> counts,
        Map<String, Long> stageCounts,
        List<DashboardIssue> issues,
        List<AutomationExecutionView> executions,
        List<IntegrationStatus> integrations
) {
}
