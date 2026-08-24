package com.workflowautomation.jiraplanner.automation.pipeline;

import com.workflowautomation.jiraplanner.automation.agent.AgentRunService;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationOrchestrator;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class DevelopmentPipelineService {
    private final AgentRunService agents;
    private final AutomationOrchestrator orchestrator;
    private final JiraGateway jira;
    private final Executor executor;
    private final Map<String, DevelopmentPipelineStatus> statuses = new ConcurrentHashMap<>();

    public DevelopmentPipelineService(AgentRunService agents, AutomationOrchestrator orchestrator, JiraGateway jira,
                                      @Qualifier("developmentTaskExecutor") Executor executor) {
        this.agents = agents;
        this.orchestrator = orchestrator;
        this.jira = jira;
        this.executor = executor;
    }

    public synchronized DevelopmentPipelineStatus trigger(String executionId) {
        AutomationExecutionView execution = orchestrator.get(executionId);
        DevelopmentPipelineStatus current = statuses.get(executionId);
        if (current != null && current.state() == DevelopmentPipelineState.RUNNING) {
            return status(execution, DevelopmentPipelineState.ALREADY_RUNNING, "Development pipeline is already running.");
        }
        if (jira.findPendingDevelopmentWork(execution.epicKey()).isEmpty()) {
            return save(status(execution, DevelopmentPipelineState.NO_STORY_TO_PROCESS,
                    "No Story is in Ready for Development."));
        }
        DevelopmentPipelineStatus running = save(status(execution, DevelopmentPipelineState.RUNNING,
                "Development pipeline started and is processing one Story."));
        executor.execute(() -> run(execution));
        return running;
    }

    public List<DevelopmentPipelineStatus> statuses() {
        return statuses.values().stream().sorted(Comparator.comparing(DevelopmentPipelineStatus::updatedAt).reversed()).toList();
    }

    private void run(AutomationExecutionView execution) {
        try {
            AutomationExecutionView result = agents.runNextDevelopment(execution.id());
            if (result.status() == ExecutionStatus.ACTIVE
                    && result.phase().name().equals("DEVELOPMENT")
                    && result.lastFailureSummary() != null && !result.lastFailureSummary().isBlank()) {
                save(status(result, DevelopmentPipelineState.RUNNING,
                        "First development attempt failed. Automatic retry is running."));
                result = agents.runNextDevelopment(execution.id());
            }
            if (result.status() == ExecutionStatus.HUMAN_REVIEW_REQUIRED) {
                save(status(result, DevelopmentPipelineState.HUMAN_INTERVENTION_REQUIRED,
                        "Development failed after retry. Human intervention is required."));
            } else if (result.lastFailureSummary() == null || result.lastFailureSummary().isBlank()) {
                save(status(result, DevelopmentPipelineState.STORY_READY_FOR_TESTING,
                        "Story development completed and the Story is ready for testing."));
            } else {
                save(status(result, DevelopmentPipelineState.FAILED,
                        "Development stopped after the configured retry policy. Review the execution details."));
            }
        } catch (RuntimeException exception) {
            save(status(execution, DevelopmentPipelineState.FAILED,
                    "Development pipeline failed: " + safeMessage(exception)));
        }
    }

    private DevelopmentPipelineStatus status(AutomationExecutionView execution, DevelopmentPipelineState state, String message) {
        return new DevelopmentPipelineStatus(execution.id(), execution.epicKey(), state, message, Instant.now());
    }
    private DevelopmentPipelineStatus save(DevelopmentPipelineStatus value) { statuses.put(value.executionId(), value); return value; }
    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(240, message.length()));
    }
}
