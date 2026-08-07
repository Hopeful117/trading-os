# Repository Analysis

## Story Overview

- **Story ID**: 0004
- **Title**: Validate Authorized Trade Plans before Execution
- **Status**: Draft
- **Location**: `docs/architecture/stories/0004-Connect approved risk evaluation to human validation and execution/story.md`
- **Author**: Trading OS Team
- **Last Modified**: 2026-08-07

## Context Summary

Story 0004 completes the transition between deterministic risk authorization (Story 0003) and trade execution (ADR-029) by introducing an explicit, persisted, auditable human validation step.

The current `ExecutionController.create()` endpoint accepts ALL execution data from the caller — including trade plan references, risk evaluation references, and execution parameters (instrument, side, quantity, etc.). This means a caller could submit an Execution Intent using:

- An invalid or non-existent RiskEvaluation
- A different Trade Plan version than what was evaluated
- Unauthorized account resources
- Execution parameters that were never evaluated by the Risk Domain

Story 0004 requires Trading Core to load authoritative Trade Plans and RiskEvaluations from persistence, verify version correspondence and account ownership, and create Execution Intents exclusively from authoritative data.

**No broker order placement is part of this Story.**

## Affected Modules & Services

| Module | Responsibility | Key Components Affected |
|--------|---------------|------------------------|
| **trading-core** | Orchestration, validation, Execution Intent creation | `ExecutionController`, `CreateExecutionIntentService`, `CreateExecutionIntentCommand`, `CreateExecutionRequest`, `RiskPersistence`, new validation service |
| **trading-core** (risk) | RiskEvaluation persistence & lookup | `RiskPersistence` (evaluation lookup by ID), `RiskEvaluationModels.Response` |
| **trading-core** (execution) | Execution domain model | `ExecutionIntent`, `TradePlanReference`, `RiskApprovalReference`, `ExecutionParameters` |
| **risk-domain** | Risk evaluation engine (read-only) | No changes expected |
| **broker-service** | Broker account facts | `BrokerAccountRepository` for ownership verification |
| **gateway** | Route exposure | New route for validation endpoint |

## Repository Structure Investigation

The Trading OS repository is a multi-service Maven project:

```
trading-core/
  src/main/java/.../execution/
    api/                    # REST controllers, DTOs
    application/            # Services, commands, ports, pipeline
    domain/                 # Aggregates, value objects, events, exceptions
    infrastructure/         # Persistence, adapters, mappers
  src/main/java/.../risk/
    api/                    # TradePlanRiskEvaluationController
    application/            # TradePlanRiskEvaluationService, RiskEvaluationModels
    infrastructure/         # RiskPersistence (entities + repository)
  src/main/java/.../brokeraccount/
    domain/                 # BrokerAccount entity
    application/            # BrokerAccountRepository

risk-domain/                # Pure risk engine (no changes expected)
gateway/                    # API gateway
docs/
  architecture/
    stories/0004-.../       # Story directory (story.md + this analysis)
    adr/ADR-031.md          # TradePlanningContext clarification
  implementation/           # ADR implementation plans
```

## Current Implementation State

### Execution Entry Point (THE PROBLEM)

`ExecutionController.create()` at `trading-core/.../execution/api/ExecutionController.java`:

```java
@PostMapping
public ResponseEntity<ExecutionDto> create(
        @Valid @RequestBody CreateExecutionRequest request,
        Authentication authentication) {
    var intent = creation.create(new CreateExecutionIntentCommand(
            new TradePlanReference(request.tradePlanId(), request.tradePlanVersion()),
            new RiskApprovalReference(request.riskEvaluationId(), request.riskDecision(),
                    request.riskApprovedAt()),
            new IdempotencyKey(request.idempotencyKey()),
            principal(authentication).getUserId(),
            request.brokerAccountId(),
            new ExecutionParameters(request.instrument(), request.side(),
                    request.orderType(), request.quantity(), request.limitPrice()),
            request.expiresAt()));
    // ...
}
```

`CreateExecutionRequest` accepts from the caller:
- `tradePlanId`, `tradePlanVersion` — caller-provided, not verified against persistence
- `riskEvaluationId`, `riskDecision`, `riskApprovedAt` — caller-provided, not verified
- `brokerAccountId` — caller-provided, not verified against account ownership
- `instrument`, `side`, `orderType`, `quantity`, `limitPrice` — caller-provided execution parameters

### RiskEvaluation Persistence (WHAT EXISTS)

`RiskPersistence` at `trading-core/.../risk/infrastructure/persistence/RiskPersistence.java` already persists:
- `RiskEvaluationEntity` — with `id`, `actorId`, `idempotencyKey`, `tradePlanId`, `tradePlanVersion`, `accountId`, `status`, `decision`, `responsePayload`
- `RiskAcknowledgmentOutboxEntity` — acknowledgment tracking
- Lookup by `(actorId, idempotencyKey)` exists

**Missing**: Lookup by evaluation ID (`evaluationId`) — currently only lookup by idempotency key exists.

### Execution Intent Aggregate (WHAT EXISTS)

`ExecutionIntent` at `trading-core/.../execution/domain/aggregate/ExecutionIntent.java`:
- Already has `TradePlanReference(tradePlanId, version)` and `RiskApprovalReference(evaluationId, decision, approvedAt)`
- Already has `ExecutionParameters(instrument, side, orderType, quantity, limitPrice)`
- Status lifecycle: `CREATED → VALIDATED → SUBMISSION_IN_PROGRESS → ...`
- `VALIDATED` status already exists in the state machine

### BrokerAccount (WHAT EXISTS)

`BrokerAccountRepository` at `trading-core/.../brokeraccount/application/BrokerAccountRepository.java`:
- `findByIdAndOwnerId(UUID id, UUID ownerId)` — ownership verification exists
- `findById(UUID id)` — basic lookup exists

### RiskEvaluationModels.Response (WHAT EXISTS)

`RiskEvaluationModels.Response` at `trading-core/.../risk/application/RiskEvaluationModels.java`:
- Contains `evaluationId`, `tradePlanId`, `tradePlanVersion`, `accountId`, `status`, `decision`, `approved`, `metrics`
- This is the authoritative risk decision record

## Required Integration Points

### 1. New REST Endpoint: Human Validation

A new endpoint in Trading Core that:
- Accepts a `tradePlanId`, `riskEvaluationId`, and `accountId` from an authenticated user
- Loads the authoritative RiskEvaluation from persistence
- Loads the authoritative Trade Plan from Market Intelligence
- Verifies Trade Plan and RiskEvaluation version correspondence
- Verifies account ownership
- Verifies BrokerAccount ownership
- Persists an immutable human validation decision
- Creates an Execution Intent from authoritative data only

### 2. RiskEvaluation Lookup by ID

`RiskPersistence` needs a new method:
```java
Optional<StoredEvaluation> evaluationById(UUID evaluationId)
```
Currently only `evaluation(UUID actorId, String key)` exists.

### 3. Trade Plan Loading

Trading Core needs a port/service to load authoritative Trade Plans from Market Intelligence.
`TradePlanRiskPort` already exists for risk evaluation — a similar read-only port is needed for validation.

### 4. Execution Intent Creation from Authoritative Data

`CreateExecutionIntentService.create()` must be adapted to accept authoritative data loaded from persistence rather than caller-provided data.

## Cross-Module Dependencies

| Dependency | Current State | Gap |
|-----------|--------------|-----|
| Trading Core → RiskPersistence | Evaluation lookup by idempotency key | Need lookup by evaluation ID |
| Trading Core → TradePlan (Market Intelligence) | `TradePlanRiskPort.load()` exists for risk | Need read-only port for validation |
| Trading Core → BrokerAccount | `BrokerAccountRepository.findByIdAndOwnerId()` exists | Already sufficient |
| Trading Core → Account | `AccountRepository.findById()` exists | Already sufficient |
| Trading Core → Execution Intent | `CreateExecutionIntentService` accepts caller data | Must accept authoritative data |
| Gateway → Trading Core | Routes to execution controller | New validation route needed |

## Gap Analysis

### Must Create

1. **New REST endpoint** — `POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations/{evaluationId}/validate`
   - Or alternatively: `POST /executions/validate` with minimal input (evaluationId + tradePlanId + version)
   
2. **New DTO** — `ValidateTradePlanRequest` or `HumanValidationRequest` with minimal caller input

3. **New service** — `HumanValidationService` (or `TradePlanValidationService`) that:
   - Loads RiskEvaluation by ID from persistence
   - Loads Trade Plan from Market Intelligence
   - Verifies version correspondence
   - Verifies account ownership
   - Verifies BrokerAccount ownership
   - Rejects non-authorized decisions
   - Persists validation record
   - Creates Execution Intent from authoritative data

4. **New persistence entity** — `HumanValidationRecord` (immutable, auditable)
   - `id`, `evaluationId`, `tradePlanId`, `tradePlanVersion`, `accountId`, `brokerAccountId`
   - `validatedBy` (userId), `validatedAt`, `decision`
   - `executionIntentId` (link to created intent)

5. **New RiskPersistence method** — `evaluationById(UUID evaluationId)`

6. **New port** — Read-only Trade Plan loading for validation context

### Must Modify

1. **`ExecutionController`** — New validation endpoint; existing `create()` may be deprecated or restricted
2. **`CreateExecutionIntentService`** — Accept authoritative data instead of caller data
3. **`CreateExecutionIntentCommand`** — Restructure to carry authoritative references
4. **`RiskPersistence`** — Add `evaluationById()` method
5. **Gateway routes** — Expose new validation endpoint

### Must Not Change

- Risk Domain module
- Risk evaluation logic
- Broker Service
- Existing execution pipeline (after Intent creation)
- Broker order placement (out of scope)

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Trade Plan version drift between risk evaluation and validation | Invalid execution | Strict version matching: RiskEvaluation.tradePlanVersion must equal TradePlan.version |
| Race condition: Trade Plan modified between evaluation and validation | Stale data | Immutable Trade Plans + version check |
| BrokerAccount ownership bypass | Unauthorized execution | Explicit `BrokerAccountRepository.findByIdAndOwnerId()` check |
| Idempotency violation | Duplicate Intents | Reuse existing `IdempotencyService` |
| RiskEvaluation not yet persisted when validation requested | 404 error | Clear error message; retry guidance |
| Execution Parameters sourced from caller instead of Trade Plan | Incorrect execution | Parameters must come exclusively from the authoritative Trade Plan |

## Recommendations

### Implementation Order

1. **RiskPersistence.evalutionById()** — Add lookup by evaluation ID (smallest change, enables everything)
2. **HumanValidationService** — Core validation logic with all verifications
3. **HumanValidationRecord entity** — Immutable persistence for audit trail
4. **New REST endpoint** — Expose validation through controller
5. **Adapt CreateExecutionIntentCommand** — Accept authoritative data
6. **Gateway route** — Expose new endpoint
7. **Tests** — Unit tests for service, integration tests for endpoint

### Architectural Notes

- The validation step sits between risk evaluation and execution — it is the "human in the loop"
- Execution Parameters must be derived from the Trade Plan, never from caller input
- The validation record is immutable — once validated, the decision cannot be changed
- Idempotent: re-validating the same evaluation produces the same result
- The existing `ExecutionIntent` aggregate and state machine remain unchanged
- Consider whether `ExecutionController.create()` should be kept or replaced by the validation endpoint

### Testing Strategy

- Unit tests for `HumanValidationService` with mocked ports
- Integration tests for the REST endpoint
- Test version mismatch rejection
- Test unauthorized account rejection
- Test non-authorized RiskEvaluation rejection
- Test idempotent validation
- Test Execution Intent creation from authoritative data
- Verify no broker order is placed
- Architecture test: validate layer dependencies remain clean
