package com.workflowautomation.jiraplanner.planning;

import com.workflowautomation.jiraplanner.config.PlannerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlanningScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlanningScheduler.class);

    private final PlanningService planningService;
    private final PlannerProperties properties;

    public PlanningScheduler(PlanningService planningService, PlannerProperties properties) {
        this.planningService = planningService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${planner.poll-delay-milliseconds:60000}")
    public void poll() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }
        try {
            planningService.processReadyEpics().forEach(result ->
                    log.info("Planning result for {}: {}", result.epicKey(), result.status()));
        } catch (RuntimeException exception) {
            log.error("Jira planning poll failed: {}", exception.getMessage());
        }
    }
}
