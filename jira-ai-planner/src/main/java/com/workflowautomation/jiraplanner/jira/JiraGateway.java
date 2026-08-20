package com.workflowautomation.jiraplanner.jira;

import com.workflowautomation.jiraplanner.planning.EpicRequirement;
import com.workflowautomation.jiraplanner.planning.StoryPlan;

import java.util.List;
import java.util.Optional;

public interface JiraGateway {

    List<String> findReadyEpicKeys(int limit);

    EpicRequirement getEpic(String epicKey);

    List<JiraIssueSnapshot> findTrackedIssues(int limit);

    Optional<String> findGeneratedIssue(String epicKey, String kind, int sequence);

    String createStory(EpicRequirement epic, StoryPlan story, int sequence);

    String createTask(EpicRequirement epic, String storyKey, StoryPlan story, int sequence, TaskKind kind);

    void markPlanned(EpicRequirement epic, int storyCount, int taskCount);

    void escalateForHumanReview(EpicRequirement epic, int attempts, List<String> reasons);

    void addAutomationComment(String issueKey, String comment);

    void requestAutomationHumanReview(String issueKey, String handoffReport);

    default List<DevelopmentWorkItem> findPendingDevelopmentWork(String epicKey) { return List.of(); }

    default void startDevelopmentWork(DevelopmentWorkItem work) { }

    default void completeDevelopmentWork(DevelopmentWorkItem work, String commitSha) { }

    default void failDevelopmentWork(DevelopmentWorkItem work, String failureReport) { }

    /** Synchronizes linked Story/Task statuses for the current automation phase. */
    default void syncAutomationPhase(String epicKey, AutomationPhase phase, String commitSha) {
        // Integrations that do not support workflow synchronization may safely remain read-only.
    }

    enum AutomationPhase {
        DEVELOPMENT, TESTING, READY_FOR_STAGING, COMPLETED, HUMAN_REVIEW
    }

    enum TaskKind {
        DEVELOPMENT("dev", "Development"),
        TESTING("test", "Testing");

        private final String label;
        private final String displayName;

        TaskKind(String label, String displayName) {
            this.label = label;
            this.displayName = displayName;
        }

        public String label() {
            return label;
        }

        public String displayName() {
            return displayName;
        }
    }
}
