package com.workflowautomation.jiraplanner.automation.pipeline;

public enum DevelopmentPipelineState {
    NO_STORY_TO_PROCESS, RUNNING, ALREADY_RUNNING, STORY_READY_FOR_TESTING,
    HUMAN_INTERVENTION_REQUIRED, FAILED
}
