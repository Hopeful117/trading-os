# Repository Analysis — Story 0034

## Story

0034 — Execution-Time Risk Revalidation

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `075d7fa` (Merge pull request #30) |
| Working tree | Clean |
| Story 0033 state | Merged (PR #29) |
| Story 0030 state | Merged (PR #26) |

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: user is final decision maker | Aligned — T1 is mandatory gate, human retains Execute authority |
| ADR-014 | Accepted | Pipeline terminates with human validation | Aligned — T1 runs before broker submission in execute pipeline |
| ADR-029 | Accepted | Execution lifecycle, idempotency, reconciliation | Aligned — T1 extends lifecycle with new terminal/non-terminal states |
| ADR-030 | Accepted | Broker Service architecture, provider isolation | Aligned — T1 uses existing RiskDomain ports; no broker changes |
| ADR-040 | Accepted | Position Management Command Architecture | Aligned — position close unaffected (ADR-040 risk semantics) |
| ADR-041 | **Accepted** | Human-Controlled Trade Execution and Financial Command Safety | **Governing ADR** — D3 mandates T1 revalidation |

---

## Current Architecture

### Risk → Execution Flow (Before Story 0034)

```
POST /executions/validate
    ↓
ValidateAndCreateService
    → loads T0 StoredEvaluation (COMPLETED + APPROVED)
    → loads TradePlan (ACCEPTED)
    → verifies ownership, version, account match
    → resolves brokerAccountId from AccountRiskConfiguration
    → deriveParameters() from plan.entryIntent
    → creates ExecutionIntent (VALIDATED)
    ↓
POST /executions/{id}/execute
    ↓
ExecuteTradeService.execute()
    → ExecutionPipelineContext
    → ExecutionValidationStep (intent state, expiration, Risk approval)
    → IdempotencyVerificationStep
    → ExecutionAttemptCreationStep
    → BrokerSubmissionStep (BROKER CALL HERE)
    → BrokerResponseProcessingStep
    → ExecutionFinalizationStep
    → events.publish()
```

**Gap:** No risk revalidation between `ValidateAndCreateService` and `BrokerSubmissionStep`. T0 stale-facts risk.

### Risk Domain (Existing Components)

| Component | Location | Reuse for T1 |
|---|---|---|
| `RiskEngine` / `RiskEngines.standard()` | `risk-domain/engine/` | Deterministic T1 evaluation |
| `RiskEvaluationContextBuilder` | `risk-domain/context/` | Build context from fresh facts |
| `TradePlanRiskEvaluationService` | `trading-core/risk/application/` | Fact loading, context assembly, provenance |
| `BrokerRiskFactsPort` | `trading-core/risk/application/port/` | Fresh broker account/position snapshots |
| `MarketValuationPort` | `trading-core/risk/application/port/` | Fresh market prices |
| `RequiredMarginPort` | `trading-core/risk/application/port/` | Fresh margin requirements |
| `RiskPersistence` | `trading-core/risk/infrastructure/persistence/` | Resolve authoritative profile (D12), persist T1 |
| `RiskProfileEntity` | `trading-core/risk/infrastructure/persistence/` | Immutable versioned profiles |
| `AccountRiskProfileAssignmentEntity` | `trading-core/risk/infrastructure/persistence/` | Account-to-profile mapping |
| `StoredEvaluation` | `trading-core/risk/infrastructure/persistence/` | T0 read-side projection |

### Execution Domain (Existing Components)

| Component | Location | Modification |
|---|---|---|
| `ExecutionIntent` aggregate | `trading-core/execution/domain/` | Add T1 states, T1 evaluation reference |
| `ExecutionStatus` | `trading-core/execution/domain/valueobject/` | Add `RISK_REVALIDATION_REJECTED`, `RISK_REVALIDATION_UNAVAILABLE` |
| `ExecutionEvent` sealed interface | `trading-core/execution/domain/event/` | Add `ExecutionIntentRiskRejected`, `ExecutionIntentRiskUnavailable` |
| `ExecutionAttempt` | `trading-core/execution/domain/` | Add `t1EvaluationId` reference |
| `ExecutionPipelineContext` | `trading-core/execution/application/pipeline/` | Carry T1 evaluation ID |
| `ExecuteTradeService` | `trading-core/execution/application/service/` | T1 gate before broker submission |
| `ExecutionController` | `trading-core/execution/api/` | Expose retry-t1 endpoint |
| `RetryExecutionService` | `trading-core/execution/application/service/` | Accept `RISK_REVALIDATION_UNAVAILABLE` |

### Persistence (Flyway)

**Current migrations:** V1–V7 (common), V3 (PostgreSQL), V8 (position close)

**Next migration:** `V9__execution_time_risk_revalidation.sql`
- `risk_evaluation_t1` table
- `execution_attempt.t1_evaluation_id` column

---

## Safety Gap Analysis (vs. ADR-041)

| Gap | Severity | ADR-041 Ref | Status |
|-----|----------|-------------|--------|
| T1 Revalidation Missing | **CRITICAL** | D3 | Story 0034 target |
| T0/T1 Audit Provenance | **HIGH** | D10 | Story 0034 target |
| Policy Identity Resolution | **HIGH** | D12 | Story 0034 target |
| T1 Rejection Semantics | **HIGH** | D13 | Story 0034 target |
| T1 Unavailable Semantics | **HIGH** | D14 | Story 0034 target |

---

## Crash-Window Analysis (Pre-T1)

| Scenario | Persisted State | Financial Risk | Recovery Path |
|---|---|---|---|
| A. Before reservation | Nothing | None | User retries |
| B. After reservation, before submission | CREATED | None | User re-initiates |
| C. During submission | SUBMITTED | Uncertain | Reconcile |
| D. Response lost | UNKNOWN | Uncertain | Reconcile |
| E. ACK before persist | SUBMITTED | Broker accepted | Reconcile → CLOSED |

**Story 0034 adds:** T1 gate BEFORE any of these scenarios. If T1 REJECTED or UNAVAILABLE, no financial command is prepared — zero financial risk.

---

## Story 0034 Scope Boundaries

### Included

- `ExecutionTimeRiskRevalidationService`: fresh deterministic risk evaluation at execution time
- T1 integration point: after `ValidateAndCreateService`, before `ExecuteTradeService` pipeline
- T1 decision persistence: `risk_evaluation_t1` table with full audit provenance
- T1 failure handling: `RISK_REVALIDATION_REJECTED` (terminal, D13), `RISK_REVALIDATION_UNAVAILABLE` (non-terminal, D14)
- T1 policy identity resolution: authoritative current account risk profile (D12)
- Retry endpoint: `POST /executions/{id}/retry-t1`
- Angular: T1 loading/rejected/unavailable states

### Excluded

- Modifying T0 risk evaluation
- New risk rules or risk engine changes
- Position close path (ADR-040)
- AI recommendations or scanner architecture
- FTMO/cTrader/prop-firm integration
- Frontend redesign
- Automatic execution or autonomous trading
- New persistent Position aggregate

---

## Files to Modify (Summary)

| Module | File | Change |
|---|---|---|
| trading-core | `ExecutionStatus.java` | Add `RISK_REVALIDATION_REJECTED`, `RISK_REVALIDATION_UNAVAILABLE` |
| trading-core | `ExecutionEvent.java` | Add `ExecutionIntentRiskRejected`, `ExecutionIntentRiskUnavailable` |
| trading-core | `ExecutionIntent.java` | Add `t1EvaluationId` reference, update `allowed()` |
| trading-core | `ExecutionLifecycleService.java` | Add T1 transition methods |
| trading-core | `ExecutionAttempt.java` | Add `t1EvaluationId` field |
| trading-core | `ExecutionAttemptEntity.java` | Add `t1EvaluationId` column |
| trading-core | `ExecutionAttemptCreationStep.java` | Set `t1EvaluationId` from context |
| trading-core | `ExecutionPipelineContext.java` | Add `t1EvaluationId()` |
| trading-core | `ExecuteTradeService.java` | Integrate T1 gate |
| trading-core | `ExecutionController.java` | Add retry-t1 endpoint |
| trading-core | `RiskPersistence.java` | Add T1 persistence methods |
| trading-os-web | `execution.model.ts` | Add T1 statuses |
| trading-os-web | `execution.service.ts` | Add `retryT1()` method |
| trading-os-web | `plan-page.ts` | T1 loading/rejected/unavailable display |
| trading-os-web | `plan-page.html` | T1 UX template |

## Files to Create (Summary)

| Module | File | Purpose |
|---|---|---|
| trading-core | `ExecutionTimeRiskRevalidationService.java` | T1 revalidation service |
| trading-core | `RetryT1ExecutionService.java` | Retry T1 for UNAVAILABLE intents |
| trading-core | `RiskEvaluationT1Entity.java` | T1 persistence entity |
| trading-core | `V9__execution_time_risk_revalidation.sql` | Database migration |
| trading-core | `V3__enforce_immutable_risk_artifacts.sql` (PostgreSQL) | Immutable trigger |

---

## Gateway

```
GATEWAY_CHANGE_REQUIRED = NO
```

Existing `executions` route at `/api/v1/executions/**` covers both new endpoints. Internal Feign calls use Eureka service discovery.
