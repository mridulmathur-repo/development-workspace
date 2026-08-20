package com.workflowautomation.jiraplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("planner")
public class PlannerProperties {

    private boolean schedulerEnabled = false;
    private long pollDelayMilliseconds = 60000;
    private int maxEpicsPerRun = 5;
    private int maxGenerationRetries = 1;

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public long getPollDelayMilliseconds() {
        return pollDelayMilliseconds;
    }

    public void setPollDelayMilliseconds(long pollDelayMilliseconds) {
        this.pollDelayMilliseconds = pollDelayMilliseconds;
    }

    public int getMaxEpicsPerRun() {
        return maxEpicsPerRun;
    }

    public void setMaxEpicsPerRun(int maxEpicsPerRun) {
        this.maxEpicsPerRun = maxEpicsPerRun;
    }

    public int getMaxGenerationRetries() {
        return maxGenerationRetries;
    }

    public void setMaxGenerationRetries(int maxGenerationRetries) {
        this.maxGenerationRetries = maxGenerationRetries;
    }
}
