package com.workflowautomation.jiraplanner.automation.handoff;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecution;
import org.springframework.stereotype.Component;

@Component
public class HumanHandoffFactory {

    public String create(AutomationExecution execution) {
        return """
                HUMAN REVIEW REQUIRED

                Execution: %s
                Epic: %s - %s
                Failed phase: %s
                Development attempts: %d
                Testing attempts: %d
                Automatic fix attempts: %d
                Human resume cycles: %d

                Failure category: %s
                Failure summary: %s
                Expected: %s
                Actual: %s
                Evidence: %s

                Git branch: %s
                Branch head commit: %s
                Last successfully tested commit: %s
                Allowed code directory: %s

                Suggested investigation: %s
                Diagnosis confidence: %s

                Authorized resume choices:
                - Resume AI Development
                - Resume AI Testing
                - Resume Staging Deployment (only if branch head equals tested commit)
                - Cancel Automation
                """.formatted(
                execution.getId(),
                execution.getEpicKey(),
                execution.getEpicSummary(),
                execution.getPhase(),
                execution.getDevelopmentAttempts(),
                execution.getTestingAttempts(),
                execution.getTotalAutomaticFixAttempts(),
                execution.getHumanResumeCount(),
                execution.getLastFailureCategory(),
                value(execution.getLastFailureSummary()),
                value(execution.getLastExpectedResult()),
                value(execution.getLastActualResult()),
                value(execution.getLastEvidenceUrl()),
                execution.getBranchName(),
                value(execution.getBranchHeadCommit()),
                value(execution.getTestedCommit()),
                execution.getAllowedDirectory(),
                value(execution.getLastSuggestedAction()),
                value(execution.getLastDiagnosisConfidence())
        );
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }
}
