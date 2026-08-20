package com.workflowautomation.jiraplanner.automation.orchestration;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecution;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionPhase;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;

import java.time.Instant;

public record AutomationExecutionView(
        String id,
        String epicKey,
        String jiraProjectKey,
        String epicSummary,
        String branchName,
        String baseBranch,
        String allowedDirectory,
        ExecutionPhase phase,
        ExecutionStatus status,
        String branchHeadCommit,
        String testedCommit,
        int developmentAttempts,
        int testingAttempts,
        int automaticFixAttemptsInCycle,
        int totalAutomaticFixAttempts,
        int humanResumeCount,
        FailureCategory lastFailureCategory,
        String lastFailureSummary,
        String lastExpectedResult,
        String lastActualResult,
        String lastEvidenceUrl,
        String lastSuggestedAction,
        String lastDiagnosisConfidence,
        String humanInstructions,
        Instant createdAt,
        Instant updatedAt
) {
    public static AutomationExecutionView from(AutomationExecution execution) {
        return new AutomationExecutionView(
                execution.getId(), execution.getEpicKey(), execution.getJiraProjectKey(),
                execution.getEpicSummary(), execution.getBranchName(), execution.getBaseBranch(),
                execution.getAllowedDirectory(), execution.getPhase(), execution.getExecutionStatus(),
                execution.getBranchHeadCommit(), execution.getTestedCommit(), execution.getDevelopmentAttempts(),
                execution.getTestingAttempts(), execution.getAutomaticFixAttemptsInCycle(),
                execution.getTotalAutomaticFixAttempts(), execution.getHumanResumeCount(),
                execution.getLastFailureCategory(), execution.getLastFailureSummary(),
                execution.getLastExpectedResult(), execution.getLastActualResult(), execution.getLastEvidenceUrl(),
                execution.getLastSuggestedAction(), execution.getLastDiagnosisConfidence(),
                execution.getHumanInstructions(), execution.getCreatedAt(), execution.getUpdatedAt()
        );
    }
}
