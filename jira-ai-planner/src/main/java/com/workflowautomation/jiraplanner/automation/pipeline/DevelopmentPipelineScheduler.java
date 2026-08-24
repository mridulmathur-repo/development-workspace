package com.workflowautomation.jiraplanner.automation.pipeline;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecutionRepository;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentPipelineScheduler {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentPipelineScheduler.class);
    private final AutomationExecutionRepository executions;
    private final DevelopmentPipelineService pipelines;
    private final AutomationProperties properties;

    public DevelopmentPipelineScheduler(AutomationExecutionRepository executions,
                                        DevelopmentPipelineService pipelines, AutomationProperties properties) {
        this.executions = executions;
        this.pipelines = pipelines;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${automation.development-poll-delay-milliseconds:300000}")
    public void poll() {
        if (!properties.isEnabled() || !properties.isDevelopmentPollerEnabled()) return;
        executions.findByExecutionStatus(ExecutionStatus.ACTIVE)
                .forEach(execution -> {
                    try {
                        DevelopmentPipelineStatus result = pipelines.trigger(execution.getId());
                        log.info("Development poll epic={} state={} message={}", execution.getEpicKey(), result.state(), result.message());
                    } catch (RuntimeException exception) {
                        log.error("Development poll failed for {}: {}", execution.getEpicKey(), exception.getMessage());
                    }
                });
    }
}
