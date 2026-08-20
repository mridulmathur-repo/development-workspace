package com.workflowautomation.jiraplanner.web;

import com.workflowautomation.jiraplanner.config.AiProperties;
import com.workflowautomation.jiraplanner.config.PlannerProperties;
import com.workflowautomation.jiraplanner.planning.PlanningResult;
import com.workflowautomation.jiraplanner.planning.PlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planning")
public class PlanningController {

    private final PlanningService planningService;
    private final PlannerProperties plannerProperties;
    private final AiProperties aiProperties;

    public PlanningController(PlanningService planningService, PlannerProperties plannerProperties,
                              AiProperties aiProperties) {
        this.planningService = planningService;
        this.plannerProperties = plannerProperties;
        this.aiProperties = aiProperties;
    }

    @PostMapping("/epics/{epicKey}")
    public ResponseEntity<PlanningResult> planEpic(@PathVariable String epicKey) {
        PlanningResult result = planningService.processEpic(epicKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run")
    public List<PlanningResult> runPlanningCycle() {
        return planningService.processReadyEpics();
    }

    @GetMapping("/settings")
    public Map<String, Object> safeSettings() {
        return Map.of(
                "schedulerEnabled", plannerProperties.isSchedulerEnabled(),
                "pollDelayMilliseconds", plannerProperties.getPollDelayMilliseconds(),
                "maxEpicsPerRun", plannerProperties.getMaxEpicsPerRun(),
                "maxGenerationRetries", plannerProperties.getMaxGenerationRetries(),
                "maximumGenerationAttempts", plannerProperties.getMaxGenerationRetries() + 1,
                "aiModel", aiProperties.getModel()
        );
    }
}
