package com.workflowautomation.jiraplanner.ai;

import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import com.workflowautomation.jiraplanner.planning.PlanningPlan;

import java.util.List;

public interface AiPlanningClient {

    PlanningPlan generatePlan(EpicRequirement epic, List<String> previousViolations);
}
