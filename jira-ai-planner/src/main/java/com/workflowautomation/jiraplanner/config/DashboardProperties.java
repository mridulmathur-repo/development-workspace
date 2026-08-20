package com.workflowautomation.jiraplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dashboard")
public class DashboardProperties {

    private int maxIssues = 200;
    private long stuckAfterMinutes = 60;

    public int getMaxIssues() {
        return maxIssues;
    }

    public void setMaxIssues(int maxIssues) {
        this.maxIssues = maxIssues;
    }

    public long getStuckAfterMinutes() {
        return stuckAfterMinutes;
    }

    public void setStuckAfterMinutes(long stuckAfterMinutes) {
        this.stuckAfterMinutes = stuckAfterMinutes;
    }
}
