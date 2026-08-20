package com.workflowautomation.jiraplanner.automation.orchestration;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecution;
import com.workflowautomation.jiraplanner.automation.execution.AutomationExecutionRepository;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionPhase;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;
import com.workflowautomation.jiraplanner.automation.execution.FailureReport;
import com.workflowautomation.jiraplanner.automation.git.BranchNameFactory;
import com.workflowautomation.jiraplanner.automation.git.BranchProvisionResult;
import com.workflowautomation.jiraplanner.automation.git.GitBranchGateway;
import com.workflowautomation.jiraplanner.automation.handoff.HumanHandoffFactory;
import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import com.workflowautomation.jiraplanner.automation.project.ProjectRegistry;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationOrchestratorTest {

    private static final String EPIC = "DMT-1";
    private static final String BRANCH = "ai/epic-dmt-1-price-range";
    private static final String COMMIT = "0123456789abcdef";

    @Mock private AutomationExecutionRepository executions;
    @Mock private JiraGateway jira;
    @Mock private ProjectRegistry projects;
    @Mock private BranchNameFactory branchNames;
    @Mock private GitBranchGateway git;
    @Mock private HumanHandoffFactory handoffs;

    private AutomationOrchestrator orchestrator;
    private ProjectDefinition project;

    @BeforeEach
    void setUp() {
        AutomationProperties properties = new AutomationProperties();
        properties.setMaxAutomaticFixAttempts(1);
        orchestrator = new AutomationOrchestrator(
                executions, jira, projects, branchNames, git, handoffs, properties);
        project = new ProjectDefinition("DMT", Path.of("C:/repo"), Path.of("C:/repo/DemoMart"),
                "main", "origin", "ai/epic-", "demomart-staging");
    }

    @Test
    void startsOneRemoteBranchForPlannedEpic() {
        when(executions.findFirstByEpicKeyAndExecutionStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(jira.getEpic(EPIC)).thenReturn(epic());
        when(projects.require("DMT")).thenReturn(project);
        when(branchNames.forEpic(project, EPIC, "Price range search")).thenReturn(BRANCH);
        when(git.ensureRemoteEpicBranch(project, BRANCH))
                .thenReturn(new BranchProvisionResult(BRANCH, COMMIT, true));

        AutomationExecutionView result = orchestrator.startEpic(EPIC);

        assertEquals(ExecutionPhase.DEVELOPMENT, result.phase());
        assertEquals(ExecutionStatus.ACTIVE, result.status());
        assertEquals(BRANCH, result.branchName());
        verify(executions).save(any(AutomationExecution.class));
        verify(jira).addAutomationComment(contains(EPIC), contains(BRANCH));
    }

    @Test
    void rejectsDevelopmentSuccessForCommitThatIsNotRemoteBranchHead() {
        AutomationExecution execution = execution();
        when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(projects.require("DMT")).thenReturn(project);
        when(git.remoteBranchHead(project, BRANCH)).thenReturn(COMMIT);

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.recordDevelopmentResult(
                        execution.getId(), true, "different-commit", null));

        verify(executions, never()).save(any());
    }

    @Test
    void requestsHumanReviewAfterConfiguredRetryIsExhausted() {
        AutomationExecution execution = execution();
        when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(handoffs.create(execution)).thenReturn("handoff report");
        FailureReport failure = new FailureReport(
                FailureCategory.DEVELOPMENT, "Compilation failed", "Build passes", "Compiler error",
                "https://jenkins/example", "Inspect changed Java file", "HIGH");

        AutomationExecutionView first = orchestrator.recordDevelopmentResult(
                execution.getId(), false, null, failure);
        AutomationExecutionView second = orchestrator.recordDevelopmentResult(
                execution.getId(), false, null, failure);

        assertEquals(ExecutionPhase.DEVELOPMENT, first.phase());
        assertEquals(ExecutionPhase.HUMAN_REVIEW, second.phase());
        assertEquals(ExecutionStatus.HUMAN_REVIEW_REQUIRED, second.status());
        verify(jira).requestAutomationHumanReview(EPIC, "handoff report");
    }

    private EpicRequirement epic() {
        return new EpicRequirement(EPIC, "DMT", "Price range search", "Description",
                "Ready for AI Planning", Set.of("ai-planned"));
    }

    private AutomationExecution execution() {
        return AutomationExecution.start(
                EPIC, "DMT", "Price range search", BRANCH, "main", "C:/repo/DemoMart", COMMIT);
    }
}
