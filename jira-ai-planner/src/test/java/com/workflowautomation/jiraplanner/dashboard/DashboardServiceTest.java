package com.workflowautomation.jiraplanner.dashboard;

import com.workflowautomation.jiraplanner.automation.execution.AutomationExecutionRepository;
import com.workflowautomation.jiraplanner.config.AiProperties;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import com.workflowautomation.jiraplanner.config.DashboardProperties;
import com.workflowautomation.jiraplanner.config.JiraProperties;
import com.workflowautomation.jiraplanner.jira.JiraGateway;
import com.workflowautomation.jiraplanner.jira.JiraIssueSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private JiraGateway jira;
    @Mock private AutomationExecutionRepository executions;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        JiraProperties jiraProperties = new JiraProperties();
        jiraProperties.setProjectKey("DMT");
        jiraProperties.setBaseUrl("https://example.atlassian.net");
        AiProperties aiProperties = new AiProperties();
        aiProperties.setApiKey("configured");
        AutomationProperties automationProperties = new AutomationProperties();
        DashboardProperties dashboardProperties = new DashboardProperties();
        dashboardProperties.setStuckAfterMinutes(30);
        service = new DashboardService(jira, executions, jiraProperties, aiProperties,
                automationProperties, dashboardProperties);
        when(executions.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());
    }

    @Test
    void classifiesDevelopmentTestingAndStuckWork() {
        when(jira.findTrackedIssues(200)).thenReturn(List.of(
                issue("DMT-2", "Story", "Price range", "In Progress", "indeterminate", 5, Set.of()),
                issue("DMT-3", "Task", "[AI][Testing] Price range", "In Progress", "indeterminate", 45,
                        Set.of("ai-test-01")),
                issue("DMT-4", "Task", "Completed task", "Done", "done", 120, Set.of())
        ));

        DashboardSnapshot snapshot = service.snapshot();

        assertEquals(1, snapshot.counts().get("stories"));
        assertEquals(2, snapshot.counts().get("tasks"));
        assertEquals(1, snapshot.counts().get("stuck"));
        assertEquals(1, snapshot.stageCounts().get("DEVELOPMENT"));
        assertEquals(1, snapshot.stageCounts().get("TESTING"));
        assertTrue(snapshot.issues().stream().filter(issue -> issue.key().equals("DMT-3"))
                .findFirst().orElseThrow().stuck());
    }

    @Test
    void returnsExecutionDataWhenJiraIsUnavailable() {
        when(jira.findTrackedIssues(200)).thenThrow(new IllegalStateException("Jira unavailable"));

        DashboardSnapshot snapshot = service.snapshot();

        assertTrue(snapshot.issues().isEmpty());
        assertEquals("ERROR", snapshot.integrations().get(0).state());
        assertTrue(snapshot.integrations().get(0).detail().contains("Jira unavailable"));
    }

    private JiraIssueSnapshot issue(String key, String type, String summary, String status,
                                    String category, long idleMinutes, Set<String> labels) {
        return new JiraIssueSnapshot(key, type, summary, status, category, "DMT-1", "AI Agent",
                Instant.now().minus(idleMinutes, ChronoUnit.MINUTES), labels);
    }
}
