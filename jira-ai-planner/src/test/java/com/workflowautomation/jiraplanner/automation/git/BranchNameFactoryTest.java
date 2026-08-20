package com.workflowautomation.jiraplanner.automation.git;

import com.workflowautomation.jiraplanner.automation.project.ProjectDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BranchNameFactoryTest {

    private final BranchNameFactory factory = new BranchNameFactory();

    @Test
    void createsOneSafeBranchNamePerEpic() {
        ProjectDefinition project = new ProjectDefinition("DMT", Path.of("."), Path.of("DemoMart"),
                "main", "origin", "ai/epic-", "demomart-staging");

        String branch = factory.forEpic(project, "DMT-1", "Price range fields & dual-handle slider!");

        assertThat(branch).isEqualTo("ai/epic-DMT-1-price-range-fields-dual-handle-slider");
    }
}
