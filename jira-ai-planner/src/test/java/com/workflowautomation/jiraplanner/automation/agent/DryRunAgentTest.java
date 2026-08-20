package com.workflowautomation.jiraplanner.automation.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DryRunAgentTest {

    private final AgentAssignment assignment = new AgentAssignment(
            "exec-1", "DMT-1", "Price range", "ai/epic-dmt-1", "abc123",
            "DemoMart", "Keep the change limited to the assigned directory");

    @Test
    void developmentDryRunReturnsAssignedCommitWithoutChangingCode() {
        AgentOutcome outcome = new DryRunDevelopmentAgent().execute(assignment);

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.commitSha()).isEqualTo("abc123");
        assertThat(outcome.failure()).isNull();
    }

    @Test
    void testingDryRunReturnsExactCommit() {
        AgentOutcome outcome = new DryRunTestingAgent().execute(assignment);

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.commitSha()).isEqualTo("abc123");
    }

    @Test
    void workersRejectUnsafeIncompleteAssignments() {
        AgentAssignment invalid = new AgentAssignment("exec-1", "DMT-1", "Summary",
                "branch", "", "", "");

        assertThatThrownBy(() -> new DryRunDevelopmentAgent().execute(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DryRunTestingAgent().execute(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
