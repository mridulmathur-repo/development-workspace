package com.workflowautomation.jiraplanner.automation.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "automation_executions")
public class AutomationExecution {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String epicKey;

    @Column(nullable = false, length = 32)
    private String jiraProjectKey;

    @Column(nullable = false, length = 250)
    private String epicSummary;

    @Column(nullable = false, length = 250)
    private String branchName;

    @Column(nullable = false, length = 100)
    private String baseBranch;

    @Column(nullable = false, length = 1024)
    private String allowedDirectory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionStatus executionStatus;

    @Column(length = 64)
    private String branchHeadCommit = "";

    @Column(length = 64)
    private String testedCommit = "";

    private int developmentAttempts;
    private int testingAttempts;
    private int automaticFixAttemptsInCycle;
    private int totalAutomaticFixAttempts;
    private int humanResumeCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private FailureCategory lastFailureCategory;

    @Lob
    private String lastFailureSummary = "";

    @Lob
    private String lastExpectedResult = "";

    @Lob
    private String lastActualResult = "";

    @Column(length = 1000)
    private String lastEvidenceUrl = "";

    @Lob
    private String lastSuggestedAction = "";

    @Column(length = 20)
    private String lastDiagnosisConfidence = "";

    @Lob
    private String humanInstructions = "";

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AutomationExecution() {
    }

    public static AutomationExecution start(String epicKey, String jiraProjectKey, String epicSummary,
                                            String branchName, String baseBranch, String allowedDirectory,
                                            String branchHeadCommit) {
        AutomationExecution execution = new AutomationExecution();
        execution.id = UUID.randomUUID().toString();
        execution.epicKey = required(epicKey, "Epic key");
        execution.jiraProjectKey = required(jiraProjectKey, "Jira project key");
        execution.epicSummary = required(epicSummary, "Epic summary");
        execution.branchName = required(branchName, "Epic branch");
        execution.baseBranch = required(baseBranch, "Base branch");
        execution.allowedDirectory = required(allowedDirectory, "Allowed directory");
        execution.branchHeadCommit = required(branchHeadCommit, "Branch head commit");
        execution.phase = ExecutionPhase.DEVELOPMENT;
        execution.executionStatus = ExecutionStatus.ACTIVE;
        execution.lastFailureCategory = FailureCategory.UNKNOWN;
        execution.createdAt = Instant.now();
        execution.updatedAt = execution.createdAt;
        return execution;
    }

    public void developmentSucceeded(String commitSha) {
        requireActivePhase(ExecutionPhase.DEVELOPMENT);
        developmentAttempts++;
        branchHeadCommit = required(commitSha, "Development commit");
        phase = ExecutionPhase.TESTING;
        clearFailure();
        touch();
    }

    public void storyDevelopmentSucceeded(String commitSha) {
        requireActivePhase(ExecutionPhase.DEVELOPMENT);
        developmentAttempts++;
        branchHeadCommit = required(commitSha, "Development commit");
        clearFailure();
        automaticFixAttemptsInCycle = 0;
        touch();
    }

    public void reopenDevelopmentCycle(String currentBranchHead) {
        if (executionStatus != ExecutionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active execution can reopen development");
        }
        if (phase == ExecutionPhase.COMPLETED || phase == ExecutionPhase.CANCELLED
                || phase == ExecutionPhase.HUMAN_REVIEW) {
            throw new IllegalStateException("Execution cannot reopen development from phase " + phase);
        }
        branchHeadCommit = required(currentBranchHead, "Current remote branch head");
        testedCommit = "";
        phase = ExecutionPhase.DEVELOPMENT;
        automaticFixAttemptsInCycle = 0;
        clearFailure();
        touch();
    }

    public boolean developmentFailed(FailureReport failure, int maxAutomaticFixAttempts) {
        requireActivePhase(ExecutionPhase.DEVELOPMENT);
        developmentAttempts++;
        applyFailure(failure);
        return retryOrRequireHuman(ExecutionPhase.DEVELOPMENT, maxAutomaticFixAttempts);
    }

    public void testingSucceeded(String commitSha) {
        requireActivePhase(ExecutionPhase.TESTING);
        String tested = required(commitSha, "Tested commit");
        if (!Objects.equals(branchHeadCommit, tested)) {
            throw new IllegalArgumentException("Testing result commit must equal the Epic branch head commit");
        }
        testingAttempts++;
        testedCommit = tested;
        phase = ExecutionPhase.STAGING_DEPLOYMENT;
        clearFailure();
        touch();
    }

    public boolean testingFailed(String commitSha, FailureReport failure, int maxAutomaticFixAttempts) {
        requireActivePhase(ExecutionPhase.TESTING);
        String tested = required(commitSha, "Tested commit");
        if (!Objects.equals(branchHeadCommit, tested)) {
            throw new IllegalArgumentException("Testing failure commit must equal the Epic branch head commit");
        }
        testingAttempts++;
        applyFailure(failure);
        return retryOrRequireHuman(ExecutionPhase.DEVELOPMENT, maxAutomaticFixAttempts);
    }

    public void deploymentSucceeded(String commitSha) {
        requireActivePhase(ExecutionPhase.STAGING_DEPLOYMENT);
        String deployed = required(commitSha, "Deployed commit");
        if (!Objects.equals(branchHeadCommit, deployed) || !Objects.equals(testedCommit, deployed)) {
            throw new IllegalArgumentException("Only the successfully tested Epic branch commit can be deployed");
        }
        phase = ExecutionPhase.COMPLETED;
        executionStatus = ExecutionStatus.COMPLETED;
        clearFailure();
        touch();
    }

    public void deploymentFailed(FailureReport failure) {
        requireActivePhase(ExecutionPhase.STAGING_DEPLOYMENT);
        applyFailure(failure);
        requireHumanReview();
    }

    public void resume(ResumePhase resumePhase, String instructions, String currentBranchHead) {
        if (executionStatus != ExecutionStatus.HUMAN_REVIEW_REQUIRED) {
            throw new IllegalStateException("Only an execution waiting for human review can be resumed");
        }
        branchHeadCommit = required(currentBranchHead, "Current remote branch head");
        ExecutionPhase requestedPhase = ExecutionPhase.valueOf(resumePhase.name());
        if (requestedPhase == ExecutionPhase.STAGING_DEPLOYMENT
                && (!Objects.equals(branchHeadCommit, testedCommit) || testedCommit.isBlank())) {
            throw new IllegalArgumentException("Staging can resume only when the branch head is the tested commit");
        }
        phase = requestedPhase;
        executionStatus = ExecutionStatus.ACTIVE;
        humanInstructions = instructions == null ? "" : instructions.trim();
        humanResumeCount++;
        automaticFixAttemptsInCycle = 0;
        touch();
    }

    public void cancel(String instructions) {
        if (executionStatus == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("A completed execution cannot be cancelled");
        }
        phase = ExecutionPhase.CANCELLED;
        executionStatus = ExecutionStatus.CANCELLED;
        humanInstructions = instructions == null ? "" : instructions.trim();
        touch();
    }

    private boolean retryOrRequireHuman(ExecutionPhase retryPhase, int maximum) {
        int allowed = Math.max(0, maximum);
        if (automaticFixAttemptsInCycle < allowed) {
            automaticFixAttemptsInCycle++;
            totalAutomaticFixAttempts++;
            phase = retryPhase;
            touch();
            return false;
        }
        requireHumanReview();
        return true;
    }

    private void requireHumanReview() {
        phase = ExecutionPhase.HUMAN_REVIEW;
        executionStatus = ExecutionStatus.HUMAN_REVIEW_REQUIRED;
        touch();
    }

    private void applyFailure(FailureReport failure) {
        FailureReport safeFailure = failure == null
                ? new FailureReport(FailureCategory.UNKNOWN, "Unknown failure", "", "", "", "", "")
                : failure;
        lastFailureCategory = safeFailure.category();
        lastFailureSummary = safeFailure.summary();
        lastExpectedResult = safeFailure.expected();
        lastActualResult = safeFailure.actual();
        lastEvidenceUrl = safeFailure.evidenceUrl();
        lastSuggestedAction = safeFailure.suggestedAction();
        lastDiagnosisConfidence = safeFailure.confidence();
    }

    private void clearFailure() {
        lastFailureCategory = FailureCategory.UNKNOWN;
        lastFailureSummary = "";
        lastExpectedResult = "";
        lastActualResult = "";
        lastEvidenceUrl = "";
        lastSuggestedAction = "";
        lastDiagnosisConfidence = "";
    }

    private void requireActivePhase(ExecutionPhase requiredPhase) {
        if (executionStatus != ExecutionStatus.ACTIVE || phase != requiredPhase) {
            throw new IllegalStateException("Execution must be active in phase " + requiredPhase);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    public String getId() { return id; }
    public String getEpicKey() { return epicKey; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public String getEpicSummary() { return epicSummary; }
    public String getBranchName() { return branchName; }
    public String getBaseBranch() { return baseBranch; }
    public String getAllowedDirectory() { return allowedDirectory; }
    public ExecutionPhase getPhase() { return phase; }
    public ExecutionStatus getExecutionStatus() { return executionStatus; }
    public String getBranchHeadCommit() { return branchHeadCommit; }
    public String getTestedCommit() { return testedCommit; }
    public int getDevelopmentAttempts() { return developmentAttempts; }
    public int getTestingAttempts() { return testingAttempts; }
    public int getAutomaticFixAttemptsInCycle() { return automaticFixAttemptsInCycle; }
    public int getTotalAutomaticFixAttempts() { return totalAutomaticFixAttempts; }
    public int getHumanResumeCount() { return humanResumeCount; }
    public FailureCategory getLastFailureCategory() { return lastFailureCategory; }
    public String getLastFailureSummary() { return lastFailureSummary; }
    public String getLastExpectedResult() { return lastExpectedResult; }
    public String getLastActualResult() { return lastActualResult; }
    public String getLastEvidenceUrl() { return lastEvidenceUrl; }
    public String getLastSuggestedAction() { return lastSuggestedAction; }
    public String getLastDiagnosisConfidence() { return lastDiagnosisConfidence; }
    public String getHumanInstructions() { return humanInstructions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
