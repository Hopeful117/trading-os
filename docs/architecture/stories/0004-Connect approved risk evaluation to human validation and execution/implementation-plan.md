# IMPLEMENTATION PLAN
## Story 0004 – Validate Authorized Trade Plans before Execution

**Related Story:** Story 0004 – Validate Authorized Trade Plans before Execution
**Related ADRs:** ADR-028, ADR-029, ADR-031

**Status:** Pending Approval

**Repository Analysis:** `docs/architecture/stories/0004-.../repository-analysis.md`

---

# Objective

This document defines the implementation strategy for Story 0004.

Unlike the Repository Analysis, which identifies gaps and risks, this document specifies:

- implementation order
- domain design decisions
- package organization
- new and modified components
- persistence changes
- API design
- testing strategy

The implementation follows an incremental approach to minimize risk while preserving architectural integrity.

---

# Architectural Decisions

## Decision 1 – Human Validation as Execution Lifecycle Transition

Human validation is **not** a new domain concept or aggregate.

It is a **lifecycle transition** within the existing Execution Domain.

The `ExecutionIntent` aggregate already defines a `CREATED → VALIDATED` transition.
This transition represents exactly the moment when an authorized Trade Plan receives
explicit human approval before entering the execution pipeline.

The current `ExecutionController.create()` skips this transition entirely — it creates
an `ExecutionIntent` and immediately allows broker submission. Story 0004 introduces
the mandatory `VALIDATED` state as a gate between creation and execution.

**Rationale:**

- ADR-029 defines `ExecutionIntent` as the aggregate that "represents the business
  authorization to execute a TradePlan" and "forms the security boundary between Risk
  approval and broker submission."
- The `VALIDATED` status already exists in `ExecutionStatus` and in the lifecycle
  state machine.
- Introducing a separate `HumanValidationRecord` aggregate would duplicate the
  responsibility that `ExecutionIntent` already owns: linking a TradePlan, a Risk
  Evaluation, and an authorized human decision into a single auditable execution
  authorization.
- The immutable audit trail is already provided by `ExecutionEvent` domain events
  (`ExecutionIntentCreated`, `ExecutionIntentValidated`).

**Consequence:** No new aggregate, no new domain entity, no new persistence table for
the validation concept itself. The validation is expressed as the authoritative creation
of an `ExecutionIntent` from persisted data, followed by an explicit `VALIDATED`
transition.

---

## Decision 2 – Fate of ExecutionController.create()

The existing `POST /executions` endpoint is **replaced** by a new validation endpoint.

The current `create()` method accepts caller-provided execution parameters, risk
evaluation references, and trade plan references — none of which are verified against
authoritative persistence. This is the core security gap identified by the Repository
Analysis.

The new endpoint accepts only the minimal input needed to locate authoritative data:

- `tradePlanId` (path)
- `tradePlanVersion` (path)
- `evaluationId` (path or body)
- `idempotencyKey` (header)

All other data (TradePlan contents, RiskEvaluation decision, account ownership,
BrokerAccount ownership, execution parameters) is loaded from persistence.

The existing `create()` method is **removed** from `ExecutionController` to prevent
bypassing authoritative validation. The `CreateExecutionRequest` DTO is replaced by
`ValidateAndCreateRequest`.

**Rationale:**

- ADR-029 states: "Risk approval is mandatory" and "ExecutionIntent is immutable after
  approval." The current endpoint violates this by accepting risk references from the
  caller.
- Keeping both endpoints would leave a bypass path. Removing the old endpoint is
  the only way to guarantee no caller can skip validation.

---

## Decision 3 – Execution Parameters from Authoritative Trade Plan

Execution parameters (`instrument`, `side`, `orderType`, `quantity`, `limitPrice`)
are **derived from the authoritative Trade Plan**, not from caller input.

The Trade Plan loaded from Market Intelligence contains the instrument, direction,
quantity, and pricing information. The validation service maps these into the
`ExecutionParameters` value object.

This guarantees that the parameters submitted to the broker are exactly the parameters
that were evaluated by the Risk Domain.

**Rationale:**

- Story 0004 acceptance criteria: "Execution Intents are created from authoritative
  Trade Plan data" and "Callers cannot provide unvalidated execution parameters."
- ADR-028: Risk evaluation is performed on immutable Trade Plan data. If execution
  parameters could differ from what was evaluated, the risk decision would be meaningless.

---

## Decision 4 – Trade Plan Loading via Existing Port

Trade Plan loading for validation reuses the existing `TradePlanRiskPort` interface.

`TradePlanRiskPort.load(UUID tradePlanId, long version)` already returns a
`TradePlanRiskPort.Snapshot` containing all data needed for validation:
`tradePlanId`, `tradePlanVersion`, `instrument`, `direction`, `quantity`,
`entryPrice`, `status`, `ownerId`, `tradingAccountId`, etc.

A new dedicated port is not justified because:

- The validation service needs the same Trade Plan snapshot that risk evaluation uses.
- Introducing a second port for the same data would create a maintenance burden and
  risk divergence.
- The port is already a clean domain interface with no infrastructure coupling.

**Rationale:**

- Repository Analysis recommendation: "prefer reuse of existing ports and domain
  abstractions over creating near-duplicate Trade Plan loading ports."
- The `TradePlanRiskPort` is a read-only port — it does not imply risk responsibility.

---

## Decision 5 – RiskEvaluation Lookup by ID

`RiskPersistence` gains a new method: `evaluationById(UUID evaluationId)`.

This is the minimal persistence change required. The existing `evaluation(actorId, key)`
method looks up by idempotency key, which is insufficient for the validation flow —
the caller provides an evaluation ID, not an idempotency key.

The method returns the same `StoredEvaluation` record type, extended with the fields
needed for validation: `status`, `decision`, `approved`, `accountId`.

**Rationale:**

- The RiskEvaluation is already persisted by `TradePlanRiskEvaluationService`. No new
  persistence entity is needed.
- The lookup is a simple primary key query on `RiskEvaluationEntity`.

---

# High-Level Architecture

```
        Authenticated User
               │
               ▼
    ┌─────────────────────┐
    │   Gateway (route)    │
    └─────────┬───────────┘
              │
              ▼
    ┌─────────────────────┐
    │  ExecutionController │  (new endpoint, old create() removed)
    │  ValidateAndCreate   │
    └─────────┬───────────┘
              │
              ▼
    ┌─────────────────────┐
    │  ValidateAndCreate   │  (application service)
    │  Service             │
    └─────────┬───────────┘
              │
    ┌─────────┼─────────────────┬──────────────────┐
    ▼         ▼                 ▼                  ▼
  RiskPersistence  TradePlanRiskPort  BrokerAccountRepo  AccountRepo
  (evaluationById) (load - existing)  (findByIdAndOwnerId) (findById)
              │
              ▼
    ┌─────────────────────┐
    │  ExecutionIntent     │  (existing aggregate)
    │  .create()           │
    │  .transition(VALIDATED)│
    └─────────┬───────────┘
              │
              ▼
    ┌─────────────────────┐
    │  ExecutionIntent     │  (persisted)
    │  RepositoryPort      │
    └─────────────────────┘
```

---

# Package Structure

No new packages are introduced.

All changes occur within existing packages:

```
execution/
    api/
        controller/     # ExecutionController — endpoint replacement
        dto/            # ValidateAndCreateRequest (replaces CreateExecutionRequest)
    application/
        command/        # ValidateAndCreateCommand (replaces CreateExecutionIntentCommand for this flow)
        service/        # ValidateAndCreateService (new application service)
    domain/
        aggregate/      # ExecutionIntent — no structural changes
        model/          # TradePlanReference, RiskApprovalReference, ExecutionParameters — no changes
        valueobject/    # ExecutionStatus — no changes
        repository/     # ExecutionIntentRepositoryPort — no changes
    infrastructure/
        persistence/    # RiskPersistence — new evaluationById() method

risk/
    infrastructure/
        persistence/    # RiskPersistence — new evaluationById() method, StoredEvaluation extended
```

---

# Component Changes

## New Components

### ValidateAndCreateRequest (DTO)

**Location:** `execution/api/dto/ValidateAndCreateRequest.java`

**Replaces:** `CreateExecutionRequest`

Fields:

```java
public record ValidateAndCreateRequest(
    @NotNull UUID tradePlanId,
    @Positive long tradePlanVersion,
    @NotNull UUID evaluationId,
    @NotBlank @Size(max = 160) String idempotencyKey,
    @NotNull UUID brokerAccountId,
    @NotNull @Future Instant expiresAt
) {}
```

Note: `instrument`, `side`, `orderType`, `quantity`, `limitPrice`, `riskDecision`,
`riskApprovedAt` are **absent** — they come from authoritative persistence.

---

### ValidateAndCreateCommand (Command)

**Location:** `execution/application/command/ValidateAndCreateCommand.java`

**Replaces:** `CreateExecutionIntentCommand` for this flow

Fields:

```java
public record ValidateAndCreateCommand(
    UUID initiatorId,
    UUID tradePlanId,
    long tradePlanVersion,
    UUID evaluationId,
    UUID brokerAccountId,
    IdempotencyKey idempotencyKey,
    Instant expiresAt,
    Instant now
) {}
```

---

### ValidateAndCreateService (Application Service)

**Location:** `execution/application/service/ValidateAndCreateService.java`

**Responsibilities:**

1. Load RiskEvaluation by ID from `RiskPersistence`
2. Load TradePlan from `TradePlanRiskPort`
3. Verify TradePlan version matches RiskEvaluation version
4. Verify TradePlan status is `ACCEPTED`
5. Verify account ownership (TradePlan.ownerId == initiatorId)
6. Verify TradingAccount ownership (TradePlan.tradingAccountId == command.accountId)
7. Verify BrokerAccount ownership (`BrokerAccountRepository.findByIdAndOwnerId`)
8. Reject non-authorized RiskEvaluation outcomes (only `APPROVED` or `APPROVED_WITH_WARNINGS`)
9. Derive `ExecutionParameters` from TradePlan snapshot
10. Create `ExecutionIntent` via existing `CreateExecutionIntentService`
11. Transition to `VALIDATED` via `ExecutionLifecycleService`

**Ports consumed:**

- `RiskPersistence` (new `evaluationById`)
- `TradePlanRiskPort` (existing `load`)
- `BrokerAccountRepository` (existing `findByIdAndOwnerId`)
- `CreateExecutionIntentService` (existing)
- `ExecutionLifecycleService` (existing)
- `Clock`

---

## Modified Components

### RiskPersistence

**Change:** New method `evaluationById(UUID evaluationId)`

**Returns:** Extended `StoredEvaluation` with `status`, `decision`, `approved` fields

The existing `StoredEvaluation` record is extended:

```java
public record StoredEvaluation(
    UUID id, UUID tradePlanId, long tradePlanVersion,
    UUID accountId, String status, String decision,
    boolean approved, Response response
) {}
```

The existing `evaluation(actorId, key)` method continues to work unchanged.
The new `evaluationById` is a simple primary key query:

```java
public Optional<StoredEvaluation> evaluationById(UUID evaluationId) {
    return entityManager.find(RiskEvaluationEntity.class, evaluationId) == null
        ? Optional.empty()
        : Optional.of(new StoredEvaluation(entity.id, entity.tradePlanId,
            entity.tradePlanVersion, entity.accountId, entity.status,
            entity.decision, isApproved(entity.decision),
            read(entity.responsePayload, Response.class)));
}
```

---

### ExecutionController

**Change:** Replace `create()` with `validateAndCreate()`

**Remove:** `CreateExecutionRequest` import and `create()` method

**Add:**

```java
@PostMapping("/validate")
public ResponseEntity<ExecutionDto> validateAndCreate(
        @Valid @RequestBody ValidateAndCreateRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        Authentication authentication) {
    var intent = validation.validateAndCreate(new ValidateAndCreateCommand(
            principal(authentication).getUserId(),
            request.tradePlanId(), request.tradePlanVersion(),
            request.evaluationId(), request.brokerAccountId(),
            new IdempotencyKey(idempotencyKey),
            request.expiresAt(), clock.instant()));
    return ResponseEntity.created(URI.create("/executions/" + intent.id().value()))
            .body(ExecutionDto.from(intent));
}
```

**Endpoint change:**

| Before | After |
|--------|-------|
| `POST /executions` (accepts all data) | `POST /executions/validate` (accepts minimal input) |

The `POST /executions` endpoint is **removed** to prevent bypassing validation.

---

### ExecutionIntent Entity

**Change:** Add `risk_evaluation_id` column if not already present

The `ExecutionIntentEntity` already has `riskEvaluationId` — verified in current
codebase. No schema change needed.

---

### Gateway Route

**Change:** Add route for `POST /executions/validate` → Trading Core

**Existing routes:** `POST /executions/**` already routes to Trading Core.

The new `/executions/validate` path is covered by the existing wildcard.

---

## Removed Components

### CreateExecutionRequest

**Removed** — replaced by `ValidateAndCreateRequest`.

The old DTO allowed callers to supply execution parameters, risk decision, and risk
timestamps directly. This is no longer acceptable.

---

# Persistence Changes

## Database Schema

**No new tables required.**

The `execution_intent` table already contains all necessary columns:

- `trade_plan_id`, `trade_plan_version` — TradePlan reference
- `risk_evaluation_id`, `risk_decision`, `risk_approved_at` — RiskEvaluation reference
- `instrument`, `side`, `order_type`, `quantity`, `limit_price` — Execution parameters
- `idempotency_key` — Idempotency
- `initiator_id`, `broker_account_id` — Ownership
- `status` — Lifecycle (includes `VALIDATED`)
- `created_at`, `updated_at`, `expires_at` — Timestamps

The `risk_evaluation` table already contains all necessary columns for lookup.

**The only persistence change is a new query method in `RiskPersistence`.**

---

# Implementation Phases

## Phase 1 – RiskPersistence Evaluation Lookup

### Objective

Enable RiskEvaluation lookup by ID.

### Deliverables

- `RiskPersistence.evaluationById(UUID evaluationId)` method
- Extended `StoredEvaluation` record with `status`, `decision`, `approved` fields

### Acceptance Criteria

- `evaluationById` returns correct evaluation data
- Existing `evaluation(actorId, key)` method unchanged
- Unit tests for new lookup method

---

## Phase 2 – ValidateAndCreateService

### Objective

Implement the core validation logic.

### Deliverables

- `ValidateAndCreateService` application service
- `ValidateAndCreateCommand` command record

### Validation Sequence

```
1. Load RiskEvaluation by ID
   └─ Reject if not found (404)
   └─ Reject if status != COMPLETED (422)
   └─ Reject if decision not in {APPROVED, APPROVED_WITH_WARNINGS} (422)

2. Load TradePlan from TradePlanRiskPort
   └─ Reject if not found (404)
   └─ Reject if status != ACCEPTED (422)

3. Verify version correspondence
   └─ Reject if TradePlan.version != RiskEvaluation.tradePlanVersion (409)

4. Verify account ownership
   └─ Reject if TradePlan.ownerId != initiatorId (403)
   └─ Reject if TradePlan.tradingAccountId != brokerAccountId's owner (403)

5. Verify BrokerAccount ownership
   └─ Reject if BrokerAccount.ownerId != initiatorId (403)

6. Derive ExecutionParameters from TradePlan
   └─ instrument = TradePlan.instrument
   └─ side = TradePlan.direction (LONG → BUY, SHORT → SELL)
   └─ quantity = TradePlan.quantity
   └─ orderType = MARKET (default for validation)
   └─ limitPrice = TradePlan.entryPrice (if applicable)

7. Create ExecutionIntent via CreateExecutionIntentService
   └─ TradePlanReference, RiskApprovalReference, ExecutionParameters all from authoritative data

8. Transition to VALIDATED via ExecutionLifecycleService
```

### Acceptance Criteria

- All validation steps enforce correct rejection
- Idempotent: same evaluation + same idempotency key returns same result
- Execution Intent created with authoritative data only
- Caller-provided execution parameters are never used
- Unit tests for each validation step

---

## Phase 3 – REST Endpoint

### Objective

Expose validation through the controller.

### Deliverables

- `ValidateAndCreateRequest` DTO
- Updated `ExecutionController` with `validateAndCreate()` method
- Removal of old `create()` method and `CreateExecutionRequest`
- Exception handler for validation errors

### Acceptance Criteria

- `POST /executions/validate` endpoint functional
- Old `POST /executions` endpoint removed
- `ResponseEntity` returned for all responses
- Proper HTTP status codes (201, 403, 404, 409, 422)
- Authentication required
- Integration tests for endpoint

---

## Phase 4 – Gateway & Wiring

### Objective

Connect all components and expose through Gateway.

### Deliverables

- Spring configuration wiring `ValidateAndCreateService`
- Gateway route verification
- End-to-end flow validation

### Acceptance Criteria

- Full flow from REST endpoint to persisted ExecutionIntent
- Gateway routes correctly
- No broker order placed
- End-to-end integration test

---

## Phase 5 – Testing & Validation

### Objective

Comprehensive validation.

### Deliverables

- Unit tests for `ValidateAndCreateService`
- Integration tests for `ExecutionController`
- Architecture tests for layer dependencies
- Version mismatch rejection tests
- Account ownership rejection tests
- Non-authorized decision rejection tests
- Idempotent validation tests
- End-to-end validation test

### Acceptance Criteria

- All tests pass
- No broker order placed
- Architecture compliant with ADR-028, ADR-029, ADR-031
- Code review ready

---

# Error Handling

| Condition | HTTP Status | Code | Message |
|-----------|------------|------|---------|
| Evaluation not found | 404 | EVALUATION_NOT_FOUND | Risk Evaluation not found |
| Evaluation not completed | 422 | EVALUATION_NOT_COMPLETED | Risk Evaluation has not completed |
| Decision not authorized | 422 | DECISION_NOT_AUTHORIZED | Risk decision does not authorize execution |
| TradePlan not found | 404 | TRADE_PLAN_NOT_FOUND | Trade Plan not found |
| TradePlan not accepted | 422 | TRADE_PLAN_NOT_ACCEPTED | Trade Plan is not in accepted state |
| Version mismatch | 409 | VERSION_MISMATCH | Trade Plan version does not match Risk Evaluation |
| Account not owned | 403 | ACCOUNT_FORBIDDEN | Account does not belong to the authenticated user |
| BrokerAccount not owned | 403 | BROKER_ACCOUNT_FORBIDDEN | Broker Account does not belong to the authenticated user |
| Duplicate idempotency | 409 | IDEMPOTENCY_CONFLICT | Idempotency-Key is already bound to another request |

---

# Security

- RiskEvaluation data is **never** trusted from client input — loaded from persistence
- ExecutionParameters are **never** accepted from client input — derived from TradePlan
- Account ownership is verified against authenticated user
- BrokerAccount ownership is verified against authenticated user
- Idempotency prevents duplicate Execution Intents
- No broker order is placed during validation
- Immutable audit trail through ExecutionEvents

---

# Performance

Validation introduces additional persistence reads before Execution Intent creation:

1. `RiskPersistence.evaluationById()` — primary key lookup (fast)
2. `TradePlanRiskPort.load()` — existing read (already in critical path for risk evaluation)
3. `BrokerAccountRepository.findByIdAndOwnerId()` — indexed lookup (fast)

These reads add negligible latency. No performance concerns.

---

# Testing Strategy

## Unit Tests

- `ValidateAndCreateServiceTest` — mocked ports, all validation paths
- `RiskPersistence.evaluationById` — persistence layer

## Integration Tests

- `ExecutionController.validateAndCreate` — full HTTP flow
- Database integration for persistence queries

## Architecture Tests

- Layer dependency validation (no infrastructure in domain)
- No bypass path to old `create()` endpoint

## End-to-End Tests

- Complete flow: RiskEvaluation → Validation → ExecutionIntent → VALIDATED
- Verify no broker order placed

---

# Definition of Done

Story 0004 implementation is considered complete when:

- [ ] `RiskPersistence.evaluationById()` implemented and tested
- [ ] `ValidateAndCreateService` implemented with all validation steps
- [ ] `ValidateAndCreateRequest` DTO created
- [ ] `ExecutionController.validateAndCreate()` endpoint functional
- [ ] Old `ExecutionController.create()` removed
- [ ] `CreateExecutionRequest` removed
- [ ] Gateway route verified
- [ ] All validation rejection paths tested
- [ ] Idempotent validation tested
- [ ] ExecutionIntent created from authoritative data only
- [ ] No broker order placed
- [ ] Architecture compliant with ADR-028, ADR-029, ADR-031
- [ ] All tests pass
- [ ] Code review approved
- [ ] Human commit created
