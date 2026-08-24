package com.workflowautomation.jiraplanner.automation.pipeline;

import java.time.Instant;

public record DevelopmentPipelineStatus(
        String executionId, String epicKey, DevelopmentPipelineState state, String message, Instant updatedAt
) { }
