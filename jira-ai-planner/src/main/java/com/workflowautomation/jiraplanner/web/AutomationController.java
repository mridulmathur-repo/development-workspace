package com.workflowautomation.jiraplanner.web;

import com.workflowautomation.jiraplanner.automation.execution.FailureCategory;
import com.workflowautomation.jiraplanner.automation.execution.FailureReport;
import com.workflowautomation.jiraplanner.automation.execution.ResumePhase;
import com.workflowautomation.jiraplanner.automation.agent.AgentRunService;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationExecutionView;
import com.workflowautomation.jiraplanner.automation.orchestration.AutomationOrchestrator;
import com.workflowautomation.jiraplanner.automation.security.AutomationAuthorizer;
import com.workflowautomation.jiraplanner.config.AutomationProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.workflowautomation.jiraplanner.automation.pipeline.DevelopmentPipelineService;
import com.workflowautomation.jiraplanner.automation.pipeline.DevelopmentPipelineStatus;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    public static final String CONTROL_HEADER = "X-Automation-Control-Token";

    private final AutomationOrchestrator orchestrator;
    private final AutomationAuthorizer authorizer;
    private final AutomationProperties properties;
    private final AgentRunService agentRuns;
    private final DevelopmentPipelineService developmentPipelines;

    public AutomationController(AutomationOrchestrator orchestrator, AutomationAuthorizer authorizer,
                                AutomationProperties properties, AgentRunService agentRuns,
                                DevelopmentPipelineService developmentPipelines) {
        this.orchestrator = orchestrator;
        this.authorizer = authorizer;
        this.properties = properties;
        this.agentRuns = agentRuns;
        this.developmentPipelines = developmentPipelines;
    }

    @PostMapping("/executions/{id}/trigger-development")
    public DevelopmentPipelineStatus triggerDevelopment(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return developmentPipelines.trigger(id);
    }

    @GetMapping("/pipeline-status")
    public List<DevelopmentPipelineStatus> pipelineStatus(
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return developmentPipelines.statuses();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", properties.isEnabled());
        response.put("maxAutomaticFixAttempts", properties.getMaxAutomaticFixAttempts());
        response.put("configuredProjectKeys", properties.getProjects().stream()
                .map(AutomationProperties.Project::getJiraProjectKey)
                .filter(value -> value != null && !value.isBlank())
                .toList());
        response.put("controlTokenConfigured",
                properties.getControlToken() != null && !properties.getControlToken().isBlank());
        return response;
    }

    @PostMapping("/epics/{epicKey}/start")
    public ResponseEntity<AutomationExecutionView> start(
            @PathVariable String epicKey,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return ResponseEntity.ok(orchestrator.startEpic(epicKey));
    }

    @GetMapping("/epics/{epicKey}/latest")
    public AutomationExecutionView latest(
            @PathVariable String epicKey,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.latestForEpic(epicKey);
    }

    @GetMapping("/executions/{id}")
    public AutomationExecutionView get(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.get(id);
    }

    @PostMapping("/executions/{id}/development-result")
    public AutomationExecutionView developmentResult(
            @PathVariable String id,
            @Valid @RequestBody AgentResultRequest request,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.recordDevelopmentResult(id, request.isSuccessful(), request.commitSha(), request.failure());
    }

    @PostMapping("/executions/{id}/testing-result")
    public AutomationExecutionView testingResult(
            @PathVariable String id,
            @Valid @RequestBody AgentResultRequest request,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.recordTestingResult(id, request.isSuccessful(), request.commitSha(), request.failure());
    }

    @PostMapping("/executions/{id}/deployment-result")
    public AutomationExecutionView deploymentResult(
            @PathVariable String id,
            @Valid @RequestBody AgentResultRequest request,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.recordDeploymentResult(id, request.isSuccessful(), request.commitSha(), request.failure());
    }

    @PostMapping("/executions/{id}/run-development")
    public AutomationExecutionView runDevelopment(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return agentRuns.runDevelopment(id);
    }

    @PostMapping("/executions/{id}/run-next-story")
    public AutomationExecutionView runNextStory(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return agentRuns.runNextDevelopment(id);
    }

    @PostMapping("/executions/{id}/run-testing")
    public AutomationExecutionView runTesting(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return agentRuns.runTesting(id);
    }

    @PostMapping("/executions/{id}/resume")
    public AutomationExecutionView resume(
            @PathVariable String id,
            @Valid @RequestBody ResumeRequest request,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.resume(id, request.phase(), request.instructions());
    }

    @PostMapping("/executions/{id}/cancel")
    public AutomationExecutionView cancel(
            @PathVariable String id,
            @RequestBody(required = false) CancelRequest request,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.cancel(id, request == null ? "" : request.instructions());
    }

    @PostMapping("/executions/{id}/resync-jira")
    public AutomationExecutionView resyncJira(
            @PathVariable String id,
            @RequestHeader(value = CONTROL_HEADER, required = false) String controlToken) {
        authorizer.requireAuthorized(controlToken);
        return orchestrator.resyncJira(id);
    }

    public record AgentResultRequest(
            @NotNull Boolean successful,
            String commitSha,
            FailureCategory category,
            String summary,
            String expected,
            String actual,
            String evidenceUrl,
            String suggestedAction,
            String confidence
    ) {
        public boolean isSuccessful() {
            return Boolean.TRUE.equals(successful);
        }

        public FailureReport failure() {
            return new FailureReport(category, summary, expected, actual, evidenceUrl, suggestedAction, confidence);
        }
    }

    public record ResumeRequest(@NotNull ResumePhase phase, String instructions) {
    }

    public record CancelRequest(String instructions) {
    }
}
