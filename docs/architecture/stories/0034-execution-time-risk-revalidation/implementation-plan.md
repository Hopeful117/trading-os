# Implementation Plan — Story 0034

## Design

```
[Phase A]  T1 Persistence Model (risk_evaluation_t1 table + entity)
[Phase B]  T1 Revalidation Service (ExecutionTimeRiskRevalidationService)
[Phase C]  ExecutionIntent Lifecycle Extension (new states, transitions, events)
[Phase D]  ExecuteTradeService T1 Integration (gate before broker submission)
[Phase E]  RetryT1ExecutionService + REST Endpoint
[Phase F]  Angular T1 States (loading, rejected, unavailable)
[Phase G]  Story Artifacts (implementation-report.md, engineering-report.md, code-review.md)
```

## Critical Sequence (ADR-041)

Every execution command MUST follow this exact sequence:

```
Human clicks Execute
    ↓
ValidateAndCreateService → ExecutionIntent (VALIDATED)
    ↓
T1 Revalidation (mandatory server-side gate)
    ↓ resolve authoritative current account risk profile (D12)
    ↓ load fresh broker/market facts
    ↓ run RiskEngine with resolved policy + fresh facts
    ↓ persist T1 evaluation atomically (D15)
    ↓
if T1 APPROVED → ExecuteTradeService pipeline → broker submission
if T1 REJECTED → ExecutionIntent RISK_REVALIDATION_REJECTED (terminal, D13)
if T1 UNAVAILABLE → ExecutionIntent RISK_REVALIDATION_UNAVAILABLE (non-terminal, D14)
```

**Invariants enforced:**

| Invariant | Enforcement Point |
|---|---|
| T1 is mandatory server-side gate (D11) | `ExecuteTradeService.execute()` runs T1 before any pipeline step |
| T1 policy resolved by Trading Core (D12) | `RiskPersistence.assignedProfile()` resolves authoritative profile |
| T1 REJECTED is terminal (D13) | `ExecutionIntent.allowed()` blocks all outgoing transitions |
| T1 UNAVAILABLE is non-terminal (D14) | Transitions to VALIDATED, CANCELLED, or EXPIRED |
| T1 durable before broker execution (D15) | `TransactionTemplate` ensures atomicity |
| No `@Transactional` spanning broker call (D2) | T1 persisted in separate transaction from broker submission |
| Retry requires explicit human action (D14) | `POST /{id}/retry-t1` endpoint only |
| T0 remains historical | T0 `StoredEvaluation` unchanged; T1 is separate table |

---

## Phase A — T1 Persistence Model

### Objective

Create `risk_evaluation_t1` table and JPA entity for durable T1 audit provenance.

### Existing Components to Reuse

- `risk_evaluation` table pattern (T0 reference)
- `risk_component_snapshot` pattern for provenance
- `risk_context_snapshot` pattern for context
- `RiskEvaluationT1Entity` follows `RiskEvaluationEntity` pattern

### Files to Create

| File | Purpose |
|---|---|
| `V9__execution_time_risk_revalidation.sql` (common) | `risk_evaluation_t1` table + `execution_attempt.t1_evaluation_id` column |
| `V3__enforce_immutable_risk_artifacts.sql` (PostgreSQL) | Immutable trigger on `risk_evaluation_t1` |
| `RiskEvaluationT1Entity.java` | JPA entity (`@Immutable`, `@IdClass`) |

### Migration Schema

```sql
-- common/V9__execution_time_risk_revalidation.sql
CREATE TABLE risk_evaluation_t1 (
    id                        UUID PRIMARY KEY,
    execution_intent_id       UUID NOT NULL,
    t0_evaluation_id          UUID,
    account_id                UUID NOT NULL,
    evaluated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    status                    VARCHAR(32) NOT NULL,
    decision                  VARCHAR(32),
    unavailable_reason_code   VARCHAR(64),
    result_payload            TEXT,
    response_payload          TEXT,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_risk_eval_t1_intent ON risk_evaluation_t1 (execution_intent_id);
CREATE INDEX idx_risk_eval_t1_account ON risk_evaluation_t1 (account_id, evaluated_at);

ALTER TABLE execution_attempt
    ADD COLUMN t1_evaluation_id UUID;
```

### RiskPersistence Methods

```java
void t1Evaluation(UUID executionIntentId, UUID t0EvaluationId, UUID accountId,
                  Instant evaluatedAt, String status, String decision,
                  String unavailableReasonCode, String resultPayload, String responsePayload);

Optional<RiskEvaluationT1Entity> t1EvaluationById(UUID id);

List<RiskEvaluationT1Entity> t1EvaluationsByIntentId(UUID intentId);
```

### Invariants

- `risk_evaluation_t1` is append-only (PostgreSQL trigger prevents UPDATE/DELETE)
- Multiple T1 attempts per ExecutionIntent supported (D14)
- T1 persisted atomically BEFORE broker submission (D15_I4)
- T0 and T1 separately queryable (D15_I1)
- Policy drift reconstructable from persisted identities (D15_I5)

### Tests Required Before Proceeding

1. Migration runs cleanly on H2 and PostgreSQL
2. `RiskEvaluationT1Entity` persists and retrieves correctly
3. Multiple T1 evaluations per intent supported
4. PostgreSQL immutable trigger rejects UPDATE/DELETE

### Dependencies

None. Foundation for all subsequent phases.

---

## Phase B — T1 Revalidation Service

### Objective

Implement `ExecutionTimeRiskRevalidationService`: fresh deterministic risk evaluation using `RiskEngine` with authoritative current account risk policy and fresh execution-time facts.

### Existing Components to Reuse

- `RiskEngine` / `RiskEngines.standard()` from `risk-domain/engine/`
- `TradePlanRiskEvaluationService` fact loading pattern
- `BrokerRiskFactsPort` / `MarketValuationPort` / `RequiredMarginPort`
- `RiskPersistence.assignedProfile()` for authoritative profile resolution (D12)
- `RiskEvaluationContextBuilder` for context assembly

### Files to Create

| File | Purpose |
|---|---|
| `ExecutionTimeRiskRevalidationService.java` | T1 revalidation service (568 lines) |

### Orchestration Flow

```java
ExecutionTimeRiskRevalidationService.evaluateAndPersist(intent, now):
    1. resolve authoritative current account profile (D12)
       → RiskPersistence.assignedProfile(accountId)
    2. load fresh broker account facts
       → BrokerRiskFactsPort.getBrokerAccountFacts(brokerAccountId)
    3. load fresh positions
       → BrokerRiskFactsPort.getPositionFacts(brokerAccountId)
    4. load fresh market valuations
       → MarketValuationPort.getMarketValuations(accountId)
    5. load fresh margin requirements
       → RequiredMarginPort.getRequiredMargin(accountId)
    6. reconstruct risk-day baseline from ledger entries
    7. build RiskEvaluationContext
       → RiskEvaluationContextBuilder.build(...)
    8. run RiskEngine.evaluate() with resolved policy + fresh facts
    9. persist T1 evaluation + component snapshots + context atomically
       → TransactionTemplate ensures atomicity
    10. return T1Outcome (decision, reasonCode, approved)
```

### T1Outcome Record

```java
record T1Outcome(RiskDecision decision, String reasonCode, boolean approved) {}
```

### Invariants

- Same `RiskEngine` and evaluation mode as T0
- T1 independently resolves authoritative profile (D12)
- T1 failure does NOT introduce `@Transactional` spanning broker call (D2)
- T1 durable before broker submission (D15_I4)
- Component snapshots provide full provenance (D15_I6)

### Tests Required Before Proceeding

1. Service construction verification
2. TransactionTemplate integration
3. T1Outcome record creation for APPROVED, REJECTED, UNAVAILABLE scenarios
4. Policy resolution from RiskPersistence
5. Fresh facts loaded from ports
6. RiskEngine called with resolved policy

### Dependencies

Phase A (persistence model).

---

## Phase C — ExecutionIntent Lifecycle Extension

### Objective

Add `RISK_REVALIDATION_REJECTED` (terminal) and `RISK_REVALIDATION_UNAVAILABLE` (non-terminal) to `ExecutionStatus`, update state transitions, add events, and link `ExecutionAttempt` to T1.

### Existing Components to Reuse

- `ExecutionStatus` enum pattern
- `ExecutionEvent` sealed interface pattern
- `ExecutionIntent.allowed()` transition map
- `ExecutionAttemptEntity` JPA pattern

### Files to Modify

| File | Change |
|---|---|
| `ExecutionStatus.java` | Add `RISK_REVALIDATION_REJECTED`, `RISK_REVALIDATION_UNAVAILABLE` |
| `ExecutionEvent.java` | Add `ExecutionIntentRiskRejected`, `ExecutionIntentRiskUnavailable` |
| `ExecutionIntent.java` | Update `allowed()` transitions for new states |
| `ExecutionLifecycleService.java` | Add T1 transition methods |
| `ExecutionAttempt.java` | Add `t1EvaluationId` field |
| `ExecutionAttemptEntity.java` | Add `t1EvaluationId` column |

### State Transition Map

```
VALIDATED → RISK_REVALIDATION_REJECTED (terminal, on T1 REJECTED)
VALIDATED → RISK_REVALIDATION_UNAVAILABLE (non-terminal, on T1 UNAVAILABLE)
RISK_REVALIDATION_UNAVAILABLE → VALIDATED (on retry-t1 with fresh T1 APPROVED)
RISK_REVALIDATION_UNAVAILABLE → CANCELLED (user cancels)
RISK_REVALIDATION_UNAVAILABLE → EXPIRED (intent expires)
```

### Events

```java
ExecutionIntentRiskRejected(UUID intentId, UUID t1EvaluationId, Instant occurredAt)
ExecutionIntentRiskUnavailable(UUID intentId, UUID t1EvaluationId, String reasonCode, Instant occurredAt)
```

### Invariants

- `RISK_REVALIDATION_REJECTED` is terminal — no outgoing transitions (D13)
- `RISK_REVALIDATION_UNAVAILABLE` is non-terminal — retryable via explicit human action (D14)
- `ExecutionAttempt.t1EvaluationId` links each broker submission to the exact T1 that authorized it
- No `ExecutionAttempt` created for T1 rejection (D13)

### Tests Required Before Proceeding

1. New statuses added to enum
2. `allowed()` validates new transitions
3. Invalid transitions rejected
4. Events carry correct fields

### Dependencies

Phase A (persistence model).

---

## Phase D — ExecuteTradeService T1 Integration

### Objective

Integrate T1 gate as the first action in `ExecuteTradeService.execute()`, before any pipeline step.

### Existing Components to Reuse

- `ExecuteTradeService.execute()` entry point
- `ExecutionPipelineContext` for carrying T1 evaluation ID
- `ExecutionAttemptCreationStep` for linking attempt to T1

### Integration Point

```
ExecuteTradeService.execute(intentId):
    1. load intent (verify not terminal, not expired)
    2. *** T1 GATE ***
       → t1Revalidation.evaluateAndPersist(intent, now)
       → if T1 REJECTED → save intent (terminal), publish events, return
       → if T1 UNAVAILABLE → save intent (non-terminal), publish events, return
       → if T1 APPROVED → continue
    3. ExecutionPipelineContext (with t1EvaluationId)
    4. ExecutionValidationStep (intent state, expiration, Risk approval)
    5. IdempotencyVerificationStep
    6. ExecutionAttemptCreationStep (ExecutionAttempt linked to T1)
    7. BrokerSubmissionStep (broker call)
    8. BrokerResponseProcessingStep
    9. ExecutionFinalizationStep
    10. events.publish()
```

### Invariants

- T1 is first action — no pipeline step runs before T1
- T1 REJECTED/UNAVAILABLE → early return, no broker call
- T1 APPROVED → pipeline continues normally
- `ExecutionAttempt` linked to exact T1 evaluation that authorized it
- No `@Transactional` spanning broker call (D2)

### Tests Required Before Proceeding

1. T1 REJECTED → intent transitions to RISK_REVALIDATION_REJECTED, no broker submission
2. T1 UNAVAILABLE → intent transitions to RISK_REVALIDATION_UNAVAILABLE, no broker submission
3. T1 APPROVED → pipeline continues normally
4. T1 APPROVED from UNAVAILABLE → transitions to VALIDATED then proceeds
5. T1 APPROVED_WITH_WARNINGS → continues pipeline
6. ExecutionAttempt linked to T1 evaluation

### Dependencies

Phase A (persistence), Phase B (service), Phase C (lifecycle).

---

## Phase E — RetryT1ExecutionService + REST Endpoint

### Objective

Enable explicit human retry for `RISK_REVALIDATION_UNAVAILABLE` intents.

### Existing Components to Reuse

- `RetryExecutionService` pattern for retry logic
- `ExecutionController` REST pattern
- `@AuthenticationPrincipal` for auth

### Files to Create

| File | Purpose |
|---|---|
| `RetryT1ExecutionService.java` | Accept only UNAVAILABLE intents, validate, delegate to ExecuteTradeService |

### REST Endpoint

```
POST /executions/{id}/retry-t1
Headers: Authorization: Bearer <JWT>
Response: 200 OK + updated ExecutionResponse
Errors: 401, 403, 404 (not found), 409 (status not UNAVAILABLE), 410 (expired)
```

### Invariants

- Only `RISK_REVALIDATION_UNAVAILABLE` intents are eligible
- Expired intents cannot be retried
- Retry creates fresh T1 evaluation (new `ExecutionAttempt`)
- Automatic retry is forbidden (D14)
- Explicit human action required (D14)

### Tests Required Before Proceeding

1. Intent not found → exception
2. Status not UNAVAILABLE → exception
3. Expired intent → exception
4. Valid retry → delegates to ExecuteTradeService with fresh T1

### Dependencies

Phase C (lifecycle), Phase D (integration).

---

## Phase F — Angular T1 States

### Objective

Display T1 loading, rejected, and unavailable states in the plan page.

### Existing Components to Reuse

- `plan-page.ts` reactive state
- `execution.service.ts` service pattern
- `execution.model.ts` status types

### Files to Modify

| File | Change |
|---|---|
| `execution.model.ts` | Add `RISK_REVALIDATION_REJECTED`, `RISK_REVALIDATION_UNAVAILABLE` to status union |
| `execution.service.ts` | Add `retryT1()` method |
| `plan-page.ts` | Handle T1 states in status display |
| `plan-page.html` | T1 rejected: explanation message, no retry; T1 unavailable: explanation message, Retry button |

### UX Flow

```
T1 loading:
  → Show "Evaluating risk..." spinner between Execute click and submission

T1 REJECTED:
  → Show "Trade rejected by risk evaluation" explanation
  → No retry button (terminal)
  → Suggest new execution authorization cycle

T1 UNAVAILABLE:
  → Show "Risk evaluation unavailable" explanation with reason code
  → Show Retry button → POST /{id}/retry-t1
  → Show Cancel button
```

### Invariants

- T1 rejected: no retry button (terminal)
- T1 unavailable: retry button calls `retry-t1` endpoint
- Reason codes displayed to user
- No automatic retry in UI

### Tests Required Before Proceeding

1. T1 loading state displayed
2. T1 rejected shows explanation, no retry
3. T1 unavailable shows explanation, retry button
4. Retry button calls service

### Dependencies

Phase E (backend endpoint available).

---

## Phase G — Story Artifacts

### Objective

Complete story documentation lifecycle.

### Files to Create

| File | Content |
|---|---|
| `implementation-report.md` | Evidence of implementation: files changed, tests executed, issues found |
| `engineering-report.md` | Architecture, semantic decisions, test results, known limitations |
| `code-review.md` | Acceptance criteria verification, semantic invariants, security review |

### Dependencies

Phase F (implementation complete and validated).

---

## Phase Summary

| Phase | Objective | Depends On | Can Parallel |
|---|---|---|---|
| A | T1 persistence model | None | B, C |
| B | T1 revalidation service | A | D |
| C | ExecutionIntent lifecycle | A | D, E |
| D | ExecuteTradeService T1 integration | A, B, C | E |
| E | RetryT1 + REST endpoint | C, D | F |
| F | Angular T1 states | E | G |
| G | Story artifacts | F | — |

**Critical path:** A → B → D → E → F → G

**Parallel opportunities:**
- A ∥ C (persistence ∥ lifecycle extension)
- B ∥ C (service ∥ lifecycle)
- F ∥ G (Angular ∥ artifacts, if implementation complete)
