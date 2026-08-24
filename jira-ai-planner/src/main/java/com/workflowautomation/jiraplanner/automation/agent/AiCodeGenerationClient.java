package com.workflowautomation.jiraplanner.automation.agent;

public interface AiCodeGenerationClient {
    GeneratedCodeChange generatePatch(AgentAssignment assignment, String codeContext);
}
