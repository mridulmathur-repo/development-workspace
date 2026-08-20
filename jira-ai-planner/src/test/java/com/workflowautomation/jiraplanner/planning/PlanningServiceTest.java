package com.workflowautomation.jiraplanner.planning;

import com.workflowautomation.jiraplanner.ai.AiPlanningClient;
import com.workflowautomation.jiraplanner.config.PlannerProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {

    @Mock
    private AiPlanningClient aiClient;

    @Mock
    private JiraGateway jiraGateway;

    private PlanningService service;
    private EpicRequirement epic;
    private PlanningPlan validPlan;

    @BeforeEach
    void setUp() {
        PlannerProperties properties = new PlannerProperties();
        properties.setMaxGenerationRetries(1);
        service = new PlanningService(aiClient, jiraGateway, new PlanValidator(), properties);
        epic = new EpicRequirement("PROJECT-1", "PROJECT", "Phone purchasing",
                "Allow customers to buy a phone", "Ready for AI Planning", Set.of());
        validPlan = new PlanningPlan(List.of(
                new StoryPlan("Browse phones", "Customer views available phones", List.of(
                        new AcceptanceCriterion("phones are available", "the customer opens the catalog",
                                "the available phones are displayed")
                ))
        ));
        when(jiraGateway.getEpic("PROJECT-1")).thenReturn(epic);
    }

    @Test
    void createsOneStoryAndTwoDeliveryTasks() {
        when(aiClient.generatePlan(eq(epic), any())).thenReturn(validPlan);
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "story", 1)).thenReturn(Optional.empty());
        when(jiraGateway.createStory(epic, validPlan.stories().get(0), 1)).thenReturn("PROJECT-2");
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "dev", 1)).thenReturn(Optional.empty());
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "test", 1)).thenReturn(Optional.empty());

        PlanningResult result = service.processEpic("PROJECT-1");

        assertThat(result.status()).isEqualTo(PlanningResult.Status.COMPLETED);
        assertThat(result.storiesCreated()).isEqualTo(1);
        assertThat(result.tasksCreated()).isEqualTo(2);
        verify(jiraGateway).createTask(epic, "PROJECT-2", validPlan.stories().get(0), 1,
                JiraGateway.TaskKind.DEVELOPMENT);
        verify(jiraGateway).createTask(epic, "PROJECT-2", validPlan.stories().get(0), 1,
                JiraGateway.TaskKind.TESTING);
        verify(jiraGateway).markPlanned(epic, 1, 2);
    }

    @Test
    void retriesOnceWhenAcceptanceCriteriaAreMissing() {
        PlanningPlan invalidPlan = new PlanningPlan(List.of(
                new StoryPlan("Browse phones", "Customer views available phones", List.of())
        ));
        when(aiClient.generatePlan(eq(epic), any())).thenReturn(invalidPlan, validPlan);
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "story", 1)).thenReturn(Optional.of("PROJECT-2"));
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "dev", 1)).thenReturn(Optional.of("PROJECT-3"));
        when(jiraGateway.findGeneratedIssue("PROJECT-1", "test", 1)).thenReturn(Optional.of("PROJECT-4"));

        PlanningResult result = service.processEpic("PROJECT-1");

        assertThat(result.status()).isEqualTo(PlanningResult.Status.COMPLETED);
        assertThat(result.generationAttempts()).isEqualTo(2);
        verify(aiClient, times(2)).generatePlan(eq(epic), any());
        verify(jiraGateway, never()).escalateForHumanReview(any(), anyInt(), any());
    }

    @Test
    void escalatesAfterTheConfiguredRetryAlsoFails() {
        PlanningPlan invalidPlan = new PlanningPlan(List.of(
                new StoryPlan("Browse phones", "Customer views available phones", List.of())
        ));
        when(aiClient.generatePlan(eq(epic), any())).thenReturn(invalidPlan);

        PlanningResult result = service.processEpic("PROJECT-1");

        assertThat(result.status()).isEqualTo(PlanningResult.Status.HUMAN_REVIEW_REQUIRED);
        assertThat(result.generationAttempts()).isEqualTo(2);
        verify(jiraGateway).escalateForHumanReview(eq(epic), eq(2), any());
        verify(jiraGateway, never()).markPlanned(any(), anyInt(), anyInt());
    }

    @Test
    void doesNotCallAiForAlreadyPlannedEpic() {
        EpicRequirement planned = new EpicRequirement("PROJECT-1", "PROJECT", "Phone purchasing",
                "Allow customers to buy a phone", "Done", Set.of("ai-planned"));
        when(jiraGateway.getEpic("PROJECT-1")).thenReturn(planned);

        PlanningResult result = service.processEpic("PROJECT-1");

        assertThat(result.status()).isEqualTo(PlanningResult.Status.ALREADY_PLANNED);
        verify(aiClient, never()).generatePlan(any(), any());
    }
}
