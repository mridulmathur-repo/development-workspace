# Jira AI Planner and Delivery Orchestrator

This standalone Spring Boot service reads Jira Epics in `Ready for AI Planning`, asks an OpenAI-compatible model to break each Epic into user stories, validates mandatory acceptance criteria, and creates the Jira delivery backlog.

It is a generic workflow-automation project and has no code or build dependency on DemoMart. DemoMart can be its first target by setting `JIRA_PROJECT_KEY`, and the same service can later be pointed at another Jira project through configuration.

MVP1 creates the planned Jira backlog. MVP2 adds the guarded delivery workflow: one Git branch per Epic, persistent phase checkpoints, independent development/testing contracts, one configurable automatic correction, human escalation and controlled resume.

The MVP2 foundation does not yet contain a model-specific coding worker or Jenkins adapter. Those adapters will plug into the generic agent contracts in the next implementation slice. No application project is modified merely by starting this service.

Phase 3 currently adds safe dry-run worker implementations. They validate the assignment boundary and return the existing branch commit without calling a model, editing files or running shell commands. Protected endpoints are available for exercising the flow:

```text
POST /api/automation/executions/{id}/run-development
POST /api/automation/executions/{id}/run-testing
```

Both require `X-Automation-Control-Token`. These endpoints are intentionally dry-run until a model-backed worker and command sandbox have been reviewed.

## What it creates

For every valid AI-generated user story, the service creates:

1. One Jira Story containing a description and mandatory Given/When/Then acceptance criteria.
2. One Jira development Task.
3. One Jira testing Task.

NFT/NFR work is excluded. Development and testing tasks are deterministic templates rather than extra AI output, reducing cost and making the result predictable.

## Quality and escalation rules

- A plan without at least one story is rejected.
- A story without a summary, description or acceptance criteria is rejected.
- Every criterion requires non-empty `given`, `when` and `then` values.
- `MAX_AUTO_FIX_ATTEMPTS=1` gives the AI one correction attempt after its initial invalid response.
- If the corrected response is still invalid, the service comments on the Epic, assigns the configured gatekeeper and transitions the Epic to `Human Review Required`.
- Labels such as `ai-source-project-1` and `ai-story-01` prevent duplicates after a partial failure or rerun.
- The Epic receives `ai-planned` only after all stories and tasks exist.

## Jira preparation

Create or verify these items in the Jira project:

- Issue types: `Epic`, `Story`, `Task`.
- Workflow status: `Ready for AI Planning`.
- Workflow status: `Human Review Required`.
- A valid workflow transition from the planning status to the human-review status.
- A Jira user/API token with permission to browse, create, edit, assign and transition issues.
- The Atlassian account ID of the human gatekeeper.

The project must allow Story and Task issues to use the Epic as their parent. If your Jira issue-type names differ, set `JIRA_EPIC_ISSUE_TYPE`, `JIRA_STORY_ISSUE_TYPE` and `JIRA_TASK_ISSUE_TYPE`.

## Configure locally

Requirements: JDK 17+, Maven 3.9+, and optionally Docker Desktop.

```powershell
cd C:\Users\Lenovo\OneDrive\Documents\ChatGPT\Development\jira-ai-planner
Copy-Item .env.example .env
```

Update `.env` with a Groq API key, Jira site URL, Jira API token, project key and gatekeeper account ID. `.env` is ignored by this project's `.gitignore` and must never be committed.

For the safest first test, keep `PLANNER_SCHEDULER_ENABLED=false`. This prevents automatic polling while configuration is being verified.

## Build and run

PowerShell:

```powershell
mvn clean verify
$env:AI_API_KEY='...'
$env:JIRA_BASE_URL='https://your-company.atlassian.net'
$env:JIRA_EMAIL='...'
$env:JIRA_API_TOKEN='...'
$env:JIRA_PROJECT_KEY='PROJECT'
$env:JIRA_HUMAN_GATEKEEPER_ACCOUNT_ID='...'
mvn spring-boot:run
```

The application listens only on the local computer at `http://127.0.0.1:8100`.

Docker:

```powershell
mvn clean verify
docker compose up --build -d
docker compose logs -f
```

## Delivery dashboard UI

After the Docker service is running, open the local dashboard:

```text
http://127.0.0.1:8100/
```

The dashboard is a read-only operational view of the configured Jira project. It shows Stories and Tasks, their delivery stage, idle/stuck work, execution records, and the health of Jira, the AI provider, GitHub, staging and the local execution database. It refreshes every 30 seconds.

Reading the dashboard does not require the automation token. The token is needed only to unlock the protected controls that start, resume or cancel an automation execution; when entered, it is kept only in the current browser session and is never included in the React build.

You can tune the local attention threshold in `.env`:

```dotenv
DASHBOARD_MAX_ISSUES=200
DASHBOARD_STUCK_AFTER_MINUTES=60
```

An item marked as stuck has simply had no Jira update within that threshold. It is a prompt for a human review, not proof that the work has failed.

## Safe manual POC

Create one detailed Jira Epic but do not enable the scheduler yet. Trigger only that Epic:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8100/api/planning/epics/PROJECT-1
```

After validating the created stories and tasks, enable polling with `PLANNER_SCHEDULER_ENABLED=true`.

A manual planning cycle is also available:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8100/api/planning/run
```

Safe, non-secret settings are visible at `GET /api/planning/settings`; health is available at `GET /actuator/health`.

## MVP2 delivery workflow

MVP2 is off by default. Its mutating endpoints require both `AUTOMATION_ENABLED=true` and the `X-Automation-Control-Token` header. Each Jira project is mapped to exactly one Git repository and one allowed application directory, so the future agents receive an explicit code boundary.

For DemoMart in Docker, configure:

```dotenv
AUTOMATION_ENABLED=false
AUTOMATION_CONTROL_TOKEN=generate-a-long-random-value
AUTOMATION_PROJECT_KEY=DMT
AUTOMATION_HOST_REPOSITORY_ROOT=C:/Users/Lenovo/OneDrive/Documents/ChatGPT/Development
AUTOMATION_ALLOWED_DIRECTORY=DemoMart
AUTOMATION_BASE_BRANCH=main
AUTOMATION_GIT_REMOTE=origin
AUTOMATION_BRANCH_PREFIX=ai/epic-
AUTOMATION_STAGING_JOB=demomart-staging
MAX_DEVELOPMENT_FIX_ATTEMPTS=1
```

Docker mounts the host repository at `/workspace`; the application stores durable execution checkpoints in the `planner-data` volume. Keep automation disabled until the Git identity, Jira credentials and operator token are verified.

An Epic can start only after MVP1 has added the `ai-planned` label. Starting it creates or reuses a remote branch such as `ai/epic-dmt-1-price-range-search` from the configured base branch. It does not switch or alter the developer's current working branch.

See [MVP2 operator guide](docs/MVP2-OPERATOR-GUIDE.md) for the API calls, human handoff fields, retry behavior and phase-resume procedure.

## Runtime sequence

```text
Jira Epic -> Groq structured JSON -> local validation
          -> valid: create Story + Development Task + Testing Task
          -> invalid: regenerate once
          -> still invalid: comment + assign gatekeeper + Human Review Required
```
