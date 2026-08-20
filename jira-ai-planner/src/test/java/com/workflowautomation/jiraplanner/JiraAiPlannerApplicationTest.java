package com.workflowautomation.jiraplanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:planner-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "planner.scheduler-enabled=false",
                "automation.enabled=false"
        })
class JiraAiPlannerApplicationTest {

    @Test
    void applicationContextStartsWithAutomationDisabled() {
    }
}
