package com.workflowautomation.jiraplanner.automation.agent;

import com.workflowautomation.jiraplanner.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeContextCollectorTest {
    @TempDir Path root;

    @Test
    void includesRelevantSourceAndExcludesSecretsAndBuildOutput() throws Exception {
        Files.createDirectories(root.resolve("frontend/src"));
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("frontend/src/PriceSlider.jsx"), "export function PriceSlider() {}");
        Files.writeString(root.resolve(".env"), "AI_API_KEY=secret");
        Files.writeString(root.resolve("target/generated.java"), "secret build output");
        AiProperties properties = new AiProperties();
        CodeContextCollector collector = new CodeContextCollector(properties);
        AgentAssignment assignment = new AgentAssignment("exec", "DMT-1", "Epic", "branch", "sha",
                root.toString(), "", "DMT-2", "Price slider", "Add price slider acceptance criteria",
                "DMT-3", "Implement slider");

        String context = collector.collect(root, assignment);

        assertTrue(context.contains("PriceSlider.jsx"));
        assertFalse(context.contains("AI_API_KEY"));
        assertFalse(context.contains("secret build output"));
    }

    @Test
    void skipsOversizedCandidateAndContinuesWithSmallerRelevantFile() throws Exception {
        Files.createDirectories(root.resolve("frontend/src"));
        Files.writeString(root.resolve("pom.xml"), "price ".repeat(3000));
        Files.writeString(root.resolve("frontend/src/PriceFields.jsx"),
                "export const PriceFields = () => 'minimum maximum price';");
        AiProperties properties = new AiProperties();
        properties.setCodeMaxContextChars(2000);
        properties.setCodeMaxFiles(5);
        AgentAssignment assignment = new AgentAssignment("exec", "DMT-1", "Epic", "branch", "sha",
                root.toString(), "", "DMT-23", "Minimum maximum price", "Add minimum and maximum price fields",
                "DMT-24", "Implement minimum and maximum price fields");

        String context = new CodeContextCollector(properties).collect(root, assignment);

        assertTrue(context.contains("PriceFields.jsx"));
        assertFalse(context.contains("price ".repeat(3000)));
    }
}
