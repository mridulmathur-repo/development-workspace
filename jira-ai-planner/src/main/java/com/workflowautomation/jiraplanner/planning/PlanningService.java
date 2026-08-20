package com.workflowautomation.jiraplanner.planning;

import com.workflowautomation.jiraplanner.ai.AiPlanningClient;
import com.workflowautomation.jiraplanner.config.PlannerProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final AiPlanningClient aiPlanningClient;
    private final JiraGateway jiraGateway;
    private final PlanValidator planValidator;
    private final PlannerProperties properties;

    public PlanningService(AiPlanningClient aiPlanningClient, JiraGateway jiraGateway,
                           PlanValidator planValidator, PlannerProperties properties) {
        this.aiPlanningClient = aiPlanningClient;
        this.jiraGateway = jiraGateway;
        this.planValidator = planValidator;
        this.properties = properties;
    }

    public List<PlanningResult> processReadyEpics() {
        return jiraGateway.findReadyEpicKeys(properties.getMaxEpicsPerRun()).stream()
                .map(this::processEpic)
                .toList();
    }

    public PlanningResult processEpic(String epicKey) {
        EpicRequirement epic = jiraGateway.getEpic(epicKey);
        if (epic.alreadyPlanned()) {
            return new PlanningResult(epic.key(), PlanningResult.Status.ALREADY_PLANNED,
                    0, 0, 0, "Epic already has the ai-planned label");
        }

        int maximumAttempts = Math.max(1, properties.getMaxGenerationRetries() + 1);
        List<String> violations = List.of();
        PlanningPlan validPlan = null;
        int attempts = 0;

        while (attempts < maximumAttempts && validPlan == null) {
            attempts++;
            try {
                PlanningPlan candidate = aiPlanningClient.generatePlan(epic, violations);
                planValidator.validate(candidate);
                validPlan = candidate;
            } catch (PlanValidationException exception) {
                violations = exception.getViolations();
                log.warn("Planning attempt {} for {} failed validation: {}", attempts, epic.key(), violations);
            } catch (RuntimeException exception) {
                violations = List.of(safeMessage(exception));
                log.warn("Planning attempt {} for {} failed: {}", attempts, epic.key(), safeMessage(exception));
            }
        }

        if (validPlan == null) {
            jiraGateway.escalateForHumanReview(epic, attempts, violations);
            return new PlanningResult(epic.key(), PlanningResult.Status.HUMAN_REVIEW_REQUIRED,
                    0, 0, attempts, "AI could not generate a valid plan; gatekeeper review requested");
        }

        IssueCounts counts = createIssuesIdempotently(epic, validPlan);
        jiraGateway.markPlanned(epic, counts.stories(), counts.tasks());
        return new PlanningResult(epic.key(), PlanningResult.Status.COMPLETED,
                counts.stories(), counts.tasks(), attempts,
                "Epic planning completed with mandatory acceptance criteria");
    }

    private IssueCounts createIssuesIdempotently(EpicRequirement epic, PlanningPlan plan) {
        int storyCount = 0;
        int taskCount = 0;
        for (int index = 0; index < plan.stories().size(); index++) {
            int sequence = index + 1;
            StoryPlan story = plan.stories().get(index);
            String storyKey = jiraGateway.findGeneratedIssue(epic.key(), "story", sequence)
                    .orElseGet(() -> jiraGateway.createStory(epic, story, sequence));
            storyCount++;

            for (JiraGateway.TaskKind kind : JiraGateway.TaskKind.values()) {
                String taskKind = kind.label();
                boolean alreadyExists = jiraGateway.findGeneratedIssue(epic.key(), taskKind, sequence).isPresent();
                if (!alreadyExists) {
                    jiraGateway.createTask(epic, storyKey, story, sequence, kind);
                }
                taskCount++;
            }
        }
        return new IssueCounts(storyCount, taskCount);
    }

    private String safeMessage(RuntimeException exception) {
        List<String> messages = new ArrayList<>();
        Throwable current = exception;
        while (current != null && messages.size() < 3) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                messages.add(current.getMessage());
            }
            current = current.getCause();
        }
        return messages.isEmpty() ? exception.getClass().getSimpleName() : String.join(" -> ", messages);
    }

    private record IssueCounts(int stories, int tasks) {
    }
}
