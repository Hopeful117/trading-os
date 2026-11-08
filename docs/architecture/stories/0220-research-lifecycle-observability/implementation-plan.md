# Implementation Plan - Story 0220

## Objective

Close the confirmed research observability gaps with the smallest additive
change to the existing Java-owned lifecycle. Preserve the existing MCP names,
Gateway routes, authorization checks, report semantics and worker mechanism.

## Design

### 1. Persist bounded lifecycle evidence

Extend `ResearchExecution` and `research_executions` with:

- `attemptCount`: starts at `0`, increments only when a worker successfully
  claims a requested execution;
- `currentStage`: a bounded enum/string describing the last persisted major
  stage, beginning with `QUEUED`, then deterministic/AI stages and terminal
  stages;
- `lastProgressAt`: timestamp updated on claim and checkpoint.

Use the existing `researchId` as the asynchronous correlation identity. Do not
add a second worker ID, request ID or trace ID without evidence that the
existing identity is insufficient.

Additive domain transitions will preserve existing status semantics:

```text
REQUESTED + QUEUED
  -> RUNNING + DETERMINISTIC_ANALYSIS
  -> RUNNING + AI_INTERPRETATION / ADAPTIVE_FOLLOW_UP / FINAL_SYNTHESIS
  -> RUNNING + CHECKPOINTED
  -> COMPLETED + COMPLETED
  -> PARTIALLY_COMPLETED + PARTIALLY_COMPLETED
  -> FAILED + FAILED
  -> REQUESTED + REQUEUED
```

`CANCELLED` and `EXPIRED` remain unchanged unless an existing executable path
demonstrates that they need status-contract support in this Story.

### 2. Expose an additive status projection

Extend `ResearchExecutionResponse` with:

- `currentStage`;
- `attemptCount`;
- `lastProgressAt`;
- `retrying`, derived as a requested execution with `attemptCount > 0`;
- `reportAvailable`, derived from the existing report payload;
- `failureCategory`, a safe bounded category derived from existing failure codes.

Do not expose exception class names, exception messages, stack traces,
credentials, grant metadata or owner details. Preserve the existing response
fields and HTTP status behavior, including `202` report responses while no
report is available.

### 3. Instrument existing worker transitions

Update `ResearchApplicationService` only at existing lifecycle boundaries:

- claim and increment the attempt count;
- mark deterministic analysis before invoking the coordinator;
- mark AI/adaptive/final stages around the existing AI calls;
- checkpoint the report with `lastProgressAt` after each market;
- mark requeue explicitly while retaining the checkpoint;
- mark terminal stage and emit a structured log for completion, partial
  completion and failure;
- retain the current safe generic failure code for unexpected runtime errors.

Logs will use bounded fields such as status, stage, attempt, research ID and
failure category. No research ID or user/agent ID will be used as a metric
label. Market Intelligence currently has no Actuator/Micrometer dependency, so
this Story will not introduce a monitoring platform or an unbounded new metrics
stack. The implementation report will record metrics as not introduced and
explain why.

### 4. Persistence migration and compatibility

Add one Market Intelligence Flyway migration after V22 with defaults suitable
for existing rows. Update JPA mapping, repository conversion and in-memory test
repository behavior. Existing restore paths must remain compatible with rows
created before the new columns existed through database defaults.

### 5. MCP contract regression

Update the stale MCP test fixture to provide the mandatory exact `scope_id` and
assert that it is forwarded in the Gateway payload. Preserve the existing
tool names, bounded validation, POST no-retry behavior and GET status/report
reads.

## Tests

### Market Intelligence

- Domain transition tests for initial queue, claim/attempt increment,
  progress/checkpoint, requeue retention and terminal outcomes.
- JPA persistence test covering migration defaults and round-trip observability
  fields.
- Application service tests for deterministic completion, controlled market
  failure, AI warning/failure, adaptive follow-up and final synthesis stage
  evidence.
- Response mapping tests for retrying, report availability and safe failure
  categories.
- Existing ownership and authorization tests remain unchanged and are rerun.

### MCP adapter

- Fix the outdated start request fixture.
- Assert exact `scopeId` forwarding.
- Retain sanitized error and tool-schema tests.

### End-to-end validation

Run the complete isolated Java/MCP test path available in the repository. Do
not claim external MCP runtime validation unless a dedicated agent credential,
scope and isolated database fixture are available. Re-run the authorized
runtime only after explicit provisioning/authorization is available; no shared
runtime mutation is part of this plan.

## Documentation

After implementation:

- update `implementation-report.md` with the actual state machine, status
  contract, logs, security impact, tests and validation outcome;
- update `mcp-adapter/README.md` with bounded status polling guidance only if
  the new status fields change the recommended client behavior;
- record that no metrics stack was introduced and why;
- produce `code-review.md` and `engineering-report.md` only at their workflow
  stages.

## Files Expected To Change

- `market-intelligence/src/main/java/.../domain/research/ResearchExecution.java`
- `market-intelligence/src/main/java/.../domain/research/ResearchExecutionStage.java`
- `market-intelligence/src/main/java/.../application/research/ResearchApplicationService.java`
- `market-intelligence/src/main/java/.../adapter/persistence/JpaResearchExecutionEntity.java`
- `market-intelligence/src/main/java/.../adapter/persistence/JpaResearchExecutionRepository.java`
- `market-intelligence/src/main/java/.../adapter/web/ResearchExecutionResponse.java`
- one new Market Intelligence Flyway migration after V22
- focused Market Intelligence tests
- `mcp-adapter/tests/test_server.py`
- Story 0220 implementation report/documentation as required

No Gateway, Trading Core authorization, AI Engine or broker code should change
unless validation proves a direct contract defect within this Story's scope.

## Validation Commands

```text
cd market-intelligence && ../trading-core/mvnw -q test
cd trading-core && ./mvnw -q test
cd mcp-adapter && .venv/bin/pytest
git diff --check
```

The full repository quality scripts remain applicable if their environment is
available, but failures outside the affected modules will be reported rather
than silently changed.

## Stop Conditions

Stop and request guidance if:

- persisted/API fields require an architectural decision beyond additive
  lifecycle projection;
- a security regression appears in ownership or scope enforcement;
- the temporal consistency error returns in isolated execution;
- a complete runtime requires unauthorized shared-state mutation or paid AI;
- existing tests reveal a contradictory lifecycle contract.

## Approval Gate

Implementation must not begin until this Implementation Plan is explicitly
approved.
