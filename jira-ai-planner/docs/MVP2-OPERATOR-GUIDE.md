# MVP2 operator guide

## What this slice provides

MVP2 now has the control plane for an AI delivery workflow:

1. Read an `ai-planned` Jira Epic.
2. Resolve the configured target project and allowed source directory.
3. Create or reuse one remote Git branch for that Epic.
4. Persist the execution state in H2 so a restart does not lose progress.
5. Accept structured results from a Development Agent, a separate Testing Agent and a staging adapter.
6. Permit one automatic correction by default.
7. Send a detailed Jira handoff to the human gatekeeper when the retry is exhausted.
8. Resume from development, testing or staging when the human explicitly requests it.

The Development Agent, Testing Agent and Jenkins integration are ports in this slice; concrete AI/tool adapters are the next slice. This boundary lets the workflow and safety rules be tested independently from any paid model or live deployment.

## State flow

```text
DEVELOPMENT -> TESTING -> STAGING_DEPLOYMENT -> COMPLETED
     ^            |
     |            +-- failed test: one automatic fix -> DEVELOPMENT
     |
     +-- failed development: one automatic retry

Retry exhausted or deployment failed -> HUMAN_REVIEW
HUMAN_REVIEW -> resume DEVELOPMENT, TESTING, or eligible STAGING_DEPLOYMENT
HUMAN_REVIEW -> CANCELLED
```

Only a commit that equals the current remote Epic-branch head can be reported as developed or tested. Staging is allowed only for the same commit that passed testing.

## Safe configuration and startup

Copy `.env.example` to `.env`, fill in the Jira and AI values already used by MVP1, and set the MVP2 variables. Generate a control token in PowerShell:

```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object {[char]$_})
```

Build and start:

```powershell
cd "C:\Users\Lenovo\OneDrive\Documents\ChatGPT\Development\jira-ai-planner"
docker compose up -d --build
curl.exe --fail http://127.0.0.1:8100/actuator/health
curl.exe http://127.0.0.1:8100/api/automation/settings
```

The settings response never returns the token itself.

## Start one Epic manually

First set `AUTOMATION_ENABLED=true` and recreate the container. In your current PowerShell session, load the same control token into a variable without printing it:

```powershell
$automationHeaders = @{ 'X-Automation-Control-Token' = $env:AUTOMATION_CONTROL_TOKEN }
$execution = Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8100/api/automation/epics/DMT-1/start' `
  -Headers $automationHeaders
$execution
```

Calling start again for the same Epic returns its open execution instead of creating a second branch or execution.

## Agent result contract

Successful development result:

```powershell
$body = @{ successful = $true; commitSha = '<remote-branch-commit>' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8100/api/automation/executions/$($execution.id)/development-result" `
  -Headers $automationHeaders -ContentType 'application/json' -Body $body
```

A failure reports enough evidence for either automatic repair or human investigation:

```json
{
  "successful": false,
  "commitSha": "<remote-branch-commit>",
  "category": "TESTING",
  "summary": "Price slider API test failed",
  "expected": "Maximum equals the highest inventory price",
  "actual": "Maximum remained zero",
  "evidenceUrl": "http://localhost:8081/job/demomart-staging/42/console",
  "suggestedAction": "Inspect catalog maximum-price query and empty inventory handling",
  "confidence": "HIGH"
}
```

Valid categories are `REQUIREMENT`, `DEVELOPMENT`, `TESTING`, `DEPLOYMENT`, `INFRASTRUCTURE`, `SECURITY` and `UNKNOWN`.

## Human handoff

When automation stops, Jira receives:

- Epic and execution identifiers;
- failed phase and all attempt counts;
- expected versus actual result;
- evidence or Jenkins URL;
- remote branch, current commit and last tested commit;
- the only directory the agent may change;
- suggested investigation and diagnosis confidence;
- authorized resume choices.

The gatekeeper can update requirements or code and then resume the same execution. The service reads the latest remote branch head before resuming.

```powershell
$resume = @{
  phase = 'DEVELOPMENT'
  instructions = 'Requirement clarified in Jira; reimplement the empty-inventory behavior.'
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8100/api/automation/executions/$($execution.id)/resume" `
  -Headers $automationHeaders -ContentType 'application/json' -Body $resume
```

Change `phase` to `TESTING` when the branch is ready for another independent test. Use `STAGING_DEPLOYMENT` only when the current branch head is still the already-tested commit.

## Safety boundaries

- Automation is disabled by default.
- A secret control header protects every state-changing or execution-reading endpoint.
- The target repository must contain `.git` and the allowed directory must exist inside it.
- Branch and remote names are validated before Git commands run.
- Git commands use argument lists, not a shell.
- The service creates a remote Epic branch without changing the user's checked-out branch.
- Execution checkpoints use optimistic locking to reduce concurrent-update corruption.
- The database and secrets are ignored by Git; `.env` must never be committed.
