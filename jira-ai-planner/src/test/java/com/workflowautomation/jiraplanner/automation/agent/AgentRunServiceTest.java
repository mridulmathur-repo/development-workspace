package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.automation.execution.ExecutionPhase;
import com.workflowautomation.jiraplanner.automation.execution.ExecutionStatus;
import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationOrchestrator;
import com.workflowautomation.jiraplanner.jira.DevelopmentWorkItem;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {
    @Mock DevelopmentAgent developmentAgent;
    @Mock TestingAgent testingAgent;
    @Mock AutomationOrchestrator orchestrator;
    @Mock JiraGateway jira;

    @Test
    void developsStoriesSequentiallyAndHandsEachOneToTesting() {
        AgentRunService service = new AgentRunService(developmentAgent, testingAgent, orchestrator, jira);
        AutomationExecutionView execution = execution();
        DevelopmentWorkItem first = work(1, "DMT-2", "DMT-3", "DMT-4");
        DevelopmentWorkItem second = work(2, "DMT-5", "DMT-6", "DMT-7");
        when(orchestrator.get("exec-1")).thenReturn(execution);
        when(jira.findPendingDevelopmentWork("DMT-1")).thenReturn(List.of(first, second));
        when(developmentAgent.execute(any())).thenReturn(AgentOutcome.success("commit-1"), AgentOutcome.success("commit-2"));
        when(orchestrator.recordDevelopmentResult("exec-1", true, "commit-2", null)).thenReturn(execution);

        service.runDevelopment("exec-1");

        InOrder order = inOrder(jira, developmentAgent);
        order.verify(jira).startDevelopmentWork(first);
        order.verify(developmentAgent).execute(any());
        order.verify(jira).completeDevelopmentWork(first, "commit-1");
        order.verify(jira).startDevelopmentWork(second);
        order.verify(developmentAgent).execute(any());
        order.verify(jira).completeDevelopmentWork(second, "commit-2");
        verify(orchestrator).recordDevelopmentResult("exec-1", true, "commit-2", null);
    }

    private DevelopmentWorkItem work(int sequence, String story, String development, String testing) {
        return new DevelopmentWorkItem(sequence, story, "Story " + sequence, "Acceptance criteria",
                development, "Implementation task", testing);
    }

    private AutomationExecutionView execution() {
        Instant now = Instant.now();
        return new AutomationExecutionView("exec-1", "DMT-1", "DMT", "Epic", "ai/epic-dmt-1",
                "main", "C:/repo/DemoMart", ExecutionPhase.DEVELOPMENT, ExecutionStatus.ACTIVE,
                "base", "", 0, 0, 0, 0, 0, FailureCategory.UNKNOWN,
                "", "", "", "", "", "", "", now, now);
    }
}
