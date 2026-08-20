package com.workflowautomation.jiraplanner.automation.orchestration;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecution;
import com.workflowautomation.jiraplanner.automation.execution.AutomationExecutionRepository;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionNotFoundException;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.automation.execution.FailureReport;
import com.workflowautomation.jiraplanner.automation.execution.ResumePhase;
import com.workflowautomation.jiraplanner.automation.git.BranchNameFactory;
import com.workflowautomation.jiraplanner.automation.git.BranchProvisionResult;
import com.workflowautomation.jiraplanner.automation.git.GitBranchGateway;
import com.workflowautomation.jiraplanner.automation.handoff.HumanHandoffFactory;
import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import com.workflowautomation.jiraplanner.automation.project.ProjectRegistry;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AutomationOrchestrator {

    private static final List<ExecutionStatus> OPEN_STATUSES = List.of(
            ExecutionStatus.ACTIVE, ExecutionStatus.HUMAN_REVIEW_REQUIRED);

    private final AutomationExecutionRepository executions;
    private final JiraGateway jira;
    private final ProjectRegistry projects;
    private final BranchNameFactory branchNames;
    private final GitBranchGateway git;
    private final HumanHandoffFactory handoffs;
    private final AutomationProperties properties;

    public AutomationOrchestrator(AutomationExecutionRepository executions, JiraGateway jira,
                                  ProjectRegistry projects, BranchNameFactory branchNames,
                                  GitBranchGateway git, HumanHandoffFactory handoffs,
                                  AutomationProperties properties) {
        this.executions = executions;
        this.jira = jira;
        this.projects = projects;
        this.branchNames = branchNames;
        this.git = git;
        this.handoffs = handoffs;
        this.properties = properties;
    }

    public AutomationExecutionView startEpic(String epicKey) {
        return executions.findFirstByEpicKeyAndExecutionStatusInOrderByCreatedAtDesc(epicKey, OPEN_STATUSES)
                .map(AutomationExecutionView::from)
                .orElseGet(() -> startNewExecution(epicKey));
    }

    public AutomationExecutionView get(String id) {
        return AutomationExecutionView.from(requireExecution(id));
    }

    public AutomationExecutionView latestForEpic(String epicKey) {
        return executions.findFirstByEpicKeyOrderByCreatedAtDesc(epicKey)
                .map(AutomationExecutionView::from)
                .orElseThrow(() -> new ExecutionNotFoundException(epicKey));
    }

    public AutomationExecutionView recordDevelopmentResult(String id, boolean successful, String commitSha,
                                                           FailureReport failure) {
        AutomationExecution execution = requireExecution(id);
        boolean humanRequired = false;
        if (successful) {
            requireCurrentRemoteCommit(execution, commitSha);
            execution.developmentSucceeded(commitSha);
        } else {
            humanRequired = execution.developmentFailed(failure, properties.getMaxAutomaticFixAttempts());
        }
        executions.save(execution);
        if (humanRequired) {
            notifyHuman(execution);
        } else {
            jira.syncAutomationPhase(execution.getEpicKey(), successful
                    ? JiraGateway.AutomationPhase.TESTING : JiraGateway.AutomationPhase.DEVELOPMENT,
                    execution.getBranchHeadCommit());
            jira.addAutomationComment(execution.getEpicKey(), successful
                    ? "AI development completed at commit %s. Execution %s is ready for independent testing."
                        .formatted(execution.getBranchHeadCommit(), execution.getId())
                    : "AI development failed. Execution %s will use automatic correction attempt %d."
                        .formatted(execution.getId(), execution.getAutomaticFixAttemptsInCycle()));
        }
        return AutomationExecutionView.from(execution);
    }

    public AutomationExecutionView recordTestingResult(String id, boolean successful, String commitSha,
                                                       FailureReport failure) {
        AutomationExecution execution = requireExecution(id);
        requireCurrentRemoteCommit(execution, commitSha);
        boolean humanRequired = false;
        if (successful) {
            execution.testingSucceeded(commitSha);
        } else {
            humanRequired = execution.testingFailed(commitSha, failure, properties.getMaxAutomaticFixAttempts());
        }
        executions.save(execution);
        if (humanRequired) {
            notifyHuman(execution);
        } else {
            jira.syncAutomationPhase(execution.getEpicKey(), successful
                    ? JiraGateway.AutomationPhase.READY_FOR_STAGING : JiraGateway.AutomationPhase.DEVELOPMENT,
                    execution.getBranchHeadCommit());
            jira.addAutomationComment(execution.getEpicKey(), successful
                    ? "Independent AI testing passed for commit %s. Execution %s is ready for staging deployment."
                        .formatted(execution.getTestedCommit(), execution.getId())
                    : "Independent AI testing failed. Execution %s is returning to development for automatic fix %d."
                        .formatted(execution.getId(), execution.getAutomaticFixAttemptsInCycle()));
        }
        return AutomationExecutionView.from(execution);
    }

    public AutomationExecutionView recordDeploymentResult(String id, boolean successful, String commitSha,
                                                          FailureReport failure) {
        AutomationExecution execution = requireExecution(id);
        requireCurrentRemoteCommit(execution, commitSha);
        if (successful) {
            execution.deploymentSucceeded(commitSha);
            executions.save(execution);
            jira.syncAutomationPhase(execution.getEpicKey(), JiraGateway.AutomationPhase.COMPLETED, commitSha);
            jira.addAutomationComment(execution.getEpicKey(),
                    "Staging deployment completed for tested commit %s. Execution %s is complete."
                            .formatted(commitSha, execution.getId()));
        } else {
            execution.deploymentFailed(failure);
            executions.save(execution);
            notifyHuman(execution);
        }
        return AutomationExecutionView.from(execution);
    }

    public AutomationExecutionView resume(String id, ResumePhase phase, String instructions) {
        AutomationExecution execution = requireExecution(id);
        ProjectDefinition project = projects.require(execution.getJiraProjectKey());
        String remoteHead = git.remoteBranchHead(project, execution.getBranchName());
        execution.resume(phase, instructions, remoteHead);
        executions.save(execution);
        jira.addAutomationComment(execution.getEpicKey(),
                "Human gatekeeper resumed execution %s from %s at remote commit %s. Instructions: %s"
                        .formatted(execution.getId(), phase, remoteHead,
                                instructions == null || instructions.isBlank() ? "None" : instructions.trim()));
        return AutomationExecutionView.from(execution);
    }

    public AutomationExecutionView cancel(String id, String instructions) {
        AutomationExecution execution = requireExecution(id);
        execution.cancel(instructions);
        executions.save(execution);
        jira.addAutomationComment(execution.getEpicKey(),
                "Automation execution %s was cancelled by the human gatekeeper. Reason: %s"
                        .formatted(id, instructions == null || instructions.isBlank() ? "Not provided" : instructions));
        return AutomationExecutionView.from(execution);
    }

    public AutomationExecutionView resyncJira(String id) {
        AutomationExecution execution = requireExecution(id);
        JiraGateway.AutomationPhase phase = switch (execution.getPhase()) {
            case DEVELOPMENT -> JiraGateway.AutomationPhase.DEVELOPMENT;
            case TESTING -> JiraGateway.AutomationPhase.TESTING;
            case STAGING_DEPLOYMENT -> JiraGateway.AutomationPhase.READY_FOR_STAGING;
            case COMPLETED -> JiraGateway.AutomationPhase.COMPLETED;
            case HUMAN_REVIEW -> JiraGateway.AutomationPhase.HUMAN_REVIEW;
            case CANCELLED -> null;
        };
        if (phase != null) {
            jira.syncAutomationPhase(execution.getEpicKey(), phase,
                    execution.getTestedCommit().isBlank() ? execution.getBranchHeadCommit() : execution.getTestedCommit());
        }
        return AutomationExecutionView.from(execution);
    }

    private AutomationExecutionView startNewExecution(String epicKey) {
        EpicRequirement epic = jira.getEpic(epicKey);
        if (!epic.alreadyPlanned()) {
            throw new IllegalStateException("Epic must have the ai-planned label before development automation starts");
        }
        ProjectDefinition project = projects.require(epic.projectKey());
        String branchName = branchNames.forEpic(project, epic.key(), epic.summary());
        BranchProvisionResult branch = git.ensureRemoteEpicBranch(project, branchName);
        AutomationExecution execution = AutomationExecution.start(
                epic.key(), epic.projectKey(), epic.summary(), branch.branchName(), project.baseBranch(),
                project.allowedDirectory().toString(), branch.commitSha());
        executions.save(execution);
        jira.addAutomationComment(epic.key(),
                "MVP2 development execution %s started. GitHub Epic branch %s %s at commit %s. "
                        .formatted(execution.getId(), branch.branchName(),
                                branch.created() ? "was created" : "already existed", branch.commitSha())
                        + "Automation is restricted to " + project.allowedDirectory() + ".");
        return AutomationExecutionView.from(execution);
    }

    private AutomationExecution requireExecution(String id) {
        return executions.findById(id).orElseThrow(() -> new ExecutionNotFoundException(id));
    }

    private void requireCurrentRemoteCommit(AutomationExecution execution, String reportedCommit) {
        ProjectDefinition project = projects.require(execution.getJiraProjectKey());
        String remoteHead = git.remoteBranchHead(project, execution.getBranchName());
        if (reportedCommit == null || !Objects.equals(remoteHead, reportedCommit.trim())) {
            throw new IllegalArgumentException(
                    "Reported commit must equal the current remote Epic branch head " + remoteHead);
        }
    }

    private void notifyHuman(AutomationExecution execution) {
        jira.requestAutomationHumanReview(execution.getEpicKey(), handoffs.create(execution));
    }
}
