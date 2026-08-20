package com.workflowautomation.jiraplanner.planning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlanValidator {

    public void validate(PlanningPlan plan) {
        List<String> violations = new ArrayList<>();
        if (plan == null || plan.stories() == null || plan.stories().isEmpty()) {
            violations.add("The plan must contain at least one user story");
        } else {
            for (int storyIndex = 0; storyIndex < plan.stories().size(); storyIndex++) {
                StoryPlan story = plan.stories().get(storyIndex);
                String prefix = "Story " + (storyIndex + 1) + ": ";
                if (story == null) {
                    violations.add(prefix + "story is missing");
                    continue;
                }
                if (isBlank(story.summary())) {
                    violations.add(prefix + "summary is required");
                }
                if (isBlank(story.description())) {
                    violations.add(prefix + "description is required");
                }
                if (story.acceptanceCriteria() == null || story.acceptanceCriteria().isEmpty()) {
                    violations.add(prefix + "at least one acceptance criterion is required");
                    continue;
                }
                for (int criterionIndex = 0; criterionIndex < story.acceptanceCriteria().size(); criterionIndex++) {
                    AcceptanceCriterion criterion = story.acceptanceCriteria().get(criterionIndex);
                    String criterionPrefix = prefix + "criterion " + (criterionIndex + 1) + " ";
                    if (criterion == null || isBlank(criterion.given())) {
                        violations.add(criterionPrefix + "must include GIVEN");
                    }
                    if (criterion == null || isBlank(criterion.when())) {
                        violations.add(criterionPrefix + "must include WHEN");
                    }
                    if (criterion == null || isBlank(criterion.then())) {
                        violations.add(criterionPrefix + "must include THEN");
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new PlanValidationException(violations);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
