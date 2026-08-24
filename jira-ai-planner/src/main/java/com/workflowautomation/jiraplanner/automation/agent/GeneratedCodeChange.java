package com.workflowautomation.jiraplanner.automation.agent;

public record GeneratedCodeChange(String summary, String patch) {
    public GeneratedCodeChange {
        summary = summary == null ? "" : summary.trim();
        patch = patch == null ? "" : patch.trim();
    }
}
