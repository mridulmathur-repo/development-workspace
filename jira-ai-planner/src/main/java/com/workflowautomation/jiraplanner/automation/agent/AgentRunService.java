package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationOrchestrator;
import org.springframework.stereotype.Service;
import com.workflowautomation.jiraplanner.jira.DevelopmentWorkItem;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;

import java.util.List;

/** Runs the configured worker contracts and hands their bounded result to the orchestrator. */
@Service
public class AgentRunService {

    private final DevelopmentAgent developmentAgent;
    private final TestingAgent testingAgent;
    private final AutomationOrchestrator orchestrator;
    private final JiraGateway jira;

    public AgentRunService(DevelopmentAgent developmentAgent, TestingAgent testingAgent,
                           AutomationOrchestrator orchestrator, JiraGateway jira) {
        this.developmentAgent = developmentAgent;
        this.testingAgent = testingAgent;
        this.orchestrator = orchestrator;
        this.jira = jira;
    }

    public AutomationExecutionView runDevelopment(String executionId) {
        AutomationExecutionView execution = orchestrator.get(executionId);
        List<DevelopmentWorkItem> workItems = jira.findPendingDevelopmentWork(execution.epicKey());
        if (workItems.isEmpty()) throw new IllegalStateException("No To Do or In Development Stories were found for " + execution.epicKey());
        String latestCommit = execution.branchHeadCommit();
        for (DevelopmentWorkItem work : workItems) {
            jira.startDevelopmentWork(work);
            AgentOutcome outcome = developmentAgent.execute(assignment(execution, work, latestCommit));
            if (!outcome.successful()) {
                AutomationExecutionView result = orchestrator.recordDevelopmentResult(executionId, false, "", outcome.failure());
                if (result.status() == ExecutionStatus.HUMAN_REVIEW_REQUIRED) {
                    jira.failDevelopmentWork(work, failureComment(work, outcome));
                } else {
                    jira.addAutomationComment(work.developmentTaskKey(),
                            "AI development attempt failed and will be retried. " + outcome.failure().summary());
                }
                return result;
            }
            latestCommit = outcome.commitSha();
            jira.completeDevelopmentWork(work, latestCommit);
        }
        return orchestrator.recordDevelopmentResult(executionId, true, latestCommit, null);
    }

    public AutomationExecutionView runTesting(String executionId) {
        AutomationExecutionView execution = orchestrator.get(executionId);
        AgentOutcome outcome = testingAgent.execute(assignment(execution));
        return orchestrator.recordTestingResult(executionId, outcome.successful(), outcome.commitSha(),
                outcome.failure());
    }

    private AgentAssignment assignment(AutomationExecutionView execution) {
        return new AgentAssignment(execution.id(), execution.epicKey(), execution.epicSummary(),
                execution.branchName(), execution.branchHeadCommit(), execution.allowedDirectory(),
                execution.humanInstructions());
    }

    private AgentAssignment assignment(AutomationExecutionView execution, DevelopmentWorkItem work, String branchCommit) {
        return new AgentAssignment(execution.id(), execution.epicKey(), execution.epicSummary(),
                execution.branchName(), branchCommit, execution.allowedDirectory(), execution.humanInstructions(),
                work.storyKey(), work.storySummary(), work.storyDescription(), work.developmentTaskKey(),
                work.developmentTaskDescription());
    }

    private String failureComment(DevelopmentWorkItem work, AgentOutcome outcome) {
        return "AI development requires human review. Story: %s. Development Task: %s. Expected: %s. Actual: %s. Suggested action: %s."
                .formatted(work.storyKey(), work.developmentTaskKey(), outcome.failure().expected(),
                        outcome.failure().actual(), outcome.failure().suggestedAction());
    }
}
