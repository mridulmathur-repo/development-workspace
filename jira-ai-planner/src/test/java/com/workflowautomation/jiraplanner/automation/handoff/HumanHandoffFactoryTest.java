package com.workflowautomation.jiraplanner.automation.handoff;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecution;
import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;
import com.workflowautomation.jiraplanner.automation.execution.FailureReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HumanHandoffFactoryTest {

    @Test
    void reportContainsEvidenceGitCheckpointAndResumeChoices() {
        String commit = "1111111111111111111111111111111111111111";
        AutomationExecution execution = AutomationExecution.start("DMT-1", "DMT", "Price range slider",
                "ai/epic-DMT-1-price-range-slider", "main", "C:/repo/DemoMart", commit);
        execution.developmentFailed(new FailureReport(FailureCategory.DEVELOPMENT, "Compilation failed",
                "Build success", "Compiler error", "build/12", "Review PhoneController", "High"), 0);

        String report = new HumanHandoffFactory().create(execution);

        assertThat(report).contains("HUMAN REVIEW REQUIRED", "DMT-1", "Compilation failed",
                "ai/epic-DMT-1-price-range-slider", commit, "Resume AI Development", "build/12");
    }
}
