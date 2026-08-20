package com.workflowautomation.jiraplanner.automation.execution;

public record FailureReport(
        FailureCategory category,
        String summary,
        String expected,
        String actual,
        String evidenceUrl,
        String suggestedAction,
        String confidence
) {
    public FailureReport {
        category = category == null ? FailureCategory.UNKNOWN : category;
        summary = safe(summary);
        expected = safe(expected);
        actual = safe(actual);
        evidenceUrl = safe(evidenceUrl);
        suggestedAction = safe(suggestedAction);
        confidence = safe(confidence);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
