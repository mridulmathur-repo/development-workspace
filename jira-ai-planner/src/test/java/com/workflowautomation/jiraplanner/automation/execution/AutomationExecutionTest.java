package com.workflowautomation.jiraplanner.automation.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationExecutionTest {

    private static final String COMMIT_1 = "1111111111111111111111111111111111111111";
    private static final String COMMIT_2 = "2222222222222222222222222222222222222222";
    private static final String COMMIT_3 = "3333333333333333333333333333333333333333";

    @Test
    void successfulFlowDeploysOnlyTheIndependentlyTestedCommit() {
        AutomationExecution execution = execution();

        execution.developmentSucceeded(COMMIT_2);
        execution.testingSucceeded(COMMIT_2);
        execution.deploymentSucceeded(COMMIT_2);

        assertThat(execution.getExecutionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(execution.getPhase()).isEqualTo(ExecutionPhase.COMPLETED);
        assertThat(execution.getTestedCommit()).isEqualTo(COMMIT_2);
    }

    @Test
    void testingFailureReturnsOnceToDevelopmentThenRequiresHuman() {
        AutomationExecution execution = execution();
        FailureReport failure = failure();

        execution.developmentSucceeded(COMMIT_2);
        boolean firstFailureRequiresHuman = execution.testingFailed(COMMIT_2, failure, 1);
        execution.developmentSucceeded(COMMIT_3);
        boolean secondFailureRequiresHuman = execution.testingFailed(COMMIT_3, failure, 1);

        assertThat(firstFailureRequiresHuman).isFalse();
        assertThat(secondFailureRequiresHuman).isTrue();
        assertThat(execution.getExecutionStatus()).isEqualTo(ExecutionStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(execution.getTotalAutomaticFixAttempts()).isEqualTo(1);
        assertThat(execution.getDevelopmentAttempts()).isEqualTo(2);
        assertThat(execution.getTestingAttempts()).isEqualTo(2);
    }

    @Test
    void humanCanResumeDevelopmentAndGetsANewBoundedRetryCycle() {
        AutomationExecution execution = execution();
        execution.developmentSucceeded(COMMIT_2);
        execution.testingFailed(COMMIT_2, failure(), 0);

        execution.resume(ResumePhase.DEVELOPMENT, "Apply the approved validation fix", COMMIT_2);

        assertThat(execution.getExecutionStatus()).isEqualTo(ExecutionStatus.ACTIVE);
        assertThat(execution.getPhase()).isEqualTo(ExecutionPhase.DEVELOPMENT);
        assertThat(execution.getHumanResumeCount()).isEqualTo(1);
        assertThat(execution.getAutomaticFixAttemptsInCycle()).isZero();
        assertThat(execution.getHumanInstructions()).contains("approved validation fix");
    }

    @Test
    void refusesStagingWhenBranchHeadDiffersFromTestedCommit() {
        AutomationExecution execution = execution();
        execution.developmentSucceeded(COMMIT_2);
        execution.testingSucceeded(COMMIT_2);
        execution.deploymentFailed(new FailureReport(FailureCategory.INFRASTRUCTURE,
                "Jenkins unavailable", "Successful deployment", "Connection refused", "", "Restart Jenkins", "High"));

        assertThatThrownBy(() -> execution.resume(ResumePhase.STAGING_DEPLOYMENT, "Retry", COMMIT_3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tested commit");
    }

    @Test
    void refusesTestingResultsForAnOlderCommit() {
        AutomationExecution execution = execution();
        execution.developmentSucceeded(COMMIT_2);

        assertThatThrownBy(() -> execution.testingSucceeded(COMMIT_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("branch head");
    }

    @Test
    void readyStoryReopensActiveStagingExecutionForDevelopment() {
        AutomationExecution execution = execution();
        execution.developmentSucceeded(COMMIT_2);
        execution.testingSucceeded(COMMIT_2);

        execution.reopenDevelopmentCycle(COMMIT_2);

        assertThat(execution.getExecutionStatus()).isEqualTo(ExecutionStatus.ACTIVE);
        assertThat(execution.getPhase()).isEqualTo(ExecutionPhase.DEVELOPMENT);
        assertThat(execution.getBranchHeadCommit()).isEqualTo(COMMIT_2);
        assertThat(execution.getTestedCommit()).isBlank();
    }

    private AutomationExecution execution() {
        return AutomationExecution.start("DMT-1", "DMT", "Price range slider",
                "ai/epic-DMT-1-price-range-slider", "main", "C:/repo/DemoMart", COMMIT_1);
    }

    private FailureReport failure() {
        return new FailureReport(FailureCategory.TESTING, "Invalid range returned 200",
                "HTTP 400", "HTTP 200", "report.html", "Validate minPrice <= maxPrice", "High");
    }
}
