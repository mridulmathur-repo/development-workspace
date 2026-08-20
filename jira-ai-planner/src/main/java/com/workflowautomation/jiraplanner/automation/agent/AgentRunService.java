package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationOrchestrator;
import org.springframework.stereotype.Service;

/** Runs the configured worker contracts and hands their bounded result to the orchestrator. */
@Service
public class AgentRunService {

    private final DevelopmentAgent developmentAgent;
    private final TestingAgent testingAgent;
    private final AutomationOrchestrator orchestrator;

    public AgentRunService(DevelopmentAgent developmentAgent, TestingAgent testingAgent,
                           AutomationOrchestrator orchestrator) {
        this.developmentAgent = developmentAgent;
        this.testingAgent = testingAgent;
        this.orchestrator = orchestrator;
    }

    public AutomationExecutionView runDevelopment(String executionId) {
        AutomationExecutionView execution = orchestrator.get(executionId);
        AgentOutcome outcome = developmentAgent.execute(assignment(execution));
        return orchestrator.recordDevelopmentResult(executionId, outcome.successful(), outcome.commitSha(),
                outcome.failure());
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
}
