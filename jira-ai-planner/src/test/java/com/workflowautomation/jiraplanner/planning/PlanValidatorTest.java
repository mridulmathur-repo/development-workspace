package com.workflowautomation.jiraplanner.planning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    @Test
    void rejectsStoryWithoutAcceptanceCriteria() {
        PlanningPlan plan = new PlanningPlan(List.of(
                new StoryPlan("Browse phones", "Show available phones", List.of())
        ));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("at least one acceptance criterion");
    }

    @Test
    void rejectsIncompleteGivenWhenThenCriterion() {
        PlanningPlan plan = new PlanningPlan(List.of(
                new StoryPlan("Browse phones", "Show available phones",
                        List.of(new AcceptanceCriterion("phones exist", "", "phones are displayed")))
        ));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("must include WHEN");
    }
}
