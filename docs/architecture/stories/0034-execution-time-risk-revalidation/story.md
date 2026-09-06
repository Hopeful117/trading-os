# Story 0034 — Execution-Time Risk Revalidation

## Metadata

**ID:** `0034`

**Title:** Execution-Time Risk Revalidation

**Status:** Accepted

---

## Goal

Before a human-authorized financial command is prepared and submitted to the broker, Trading Core must independently resolve the authoritative current account risk policy and make a fresh deterministic T1 safety decision from authoritative execution-time facts.

If T1 rejects, no financial command is prepared or submitted.

T0 and T1 are separate temporal safety decisions. Policy drift between T0 and T1 is auditable information, not an error.

This closes the production-readiness gap identified in Story 0030 where the execution path relied solely on a prior T0 risk evaluation.

---

## Context

Story 0030 established the human-controlled execution loop:

```
TradePlan accepted
    ↓
T0 Risk Evaluation (APPROVED)
    ↓
Human clicks Execute
    ↓
ValidateAndCreateService → ExecutionIntent
    ↓
ExecuteTradeService pipeline
    ↓
Broker Service → Kraken
```

Story 0030 Engineering Report §Remaining Risks explicitly documented:

> **Risk Freshness (Prominent)**
> Story 0030 DOES NOT re-evaluate risk immediately before broker submission.
> Between Risk APPROVED and Human Execute, the following may change:
> - equity
> - exposure
> - positions
> - market price
> - other risk context
> Therefore Story 0030 does NOT establish unrestricted real-money production readiness.

Story 0033 Full Exposure Close operates on a different risk semantic (reducing exposure does not require TradePlan/entry RiskEvaluation per ADR-040 §Risk Semantics).

ADR-041 D3 now formally requires T1 revalidation as a mandatory architectural invariant.

---

## Problem

Three concrete issues prevent production execution readiness:

**1. Stale T0 Risk Decision:** The `StoredEvaluation` loaded by `ValidateAndCreateService` reflects market/account state at T0 (risk evaluation time). By the time the human clicks Execute and the pipeline reaches broker submission, equity, exposure, margin, prices, and positions may have changed materially.

**2. No T1 Safety Gate:** `ExecuteTradeService` pipeline begins with `ExecutionValidationStep` (verifies intent state, expiration, Risk approval) and `IdempotencyVerificationStep` — neither re-runs the Risk Engine with fresh facts.

**3. Audit Gap:** ADR-041 D3 and D10 require T0 and T1 decisions to remain separately auditable. Currently only T0 (`StoredEvaluation`) is persisted.

---

## Scope

### In Scope

- **T1 Revalidation Service:** Fresh deterministic risk evaluation at execution time using `RiskEngine` with the authoritative current account risk policy and fresh execution-time facts.
- **Integration Point:** T1 executes after `ValidateAndCreateService` creates `ExecutionIntent` (VALIDATED) but BEFORE `ExecuteTradeService` pipeline begins broker submission.
- **T1 Decision Persistence:** Durable record of T1 evaluation (decision, metrics, violations, timestamp, policy identity) linked to `ExecutionIntent` for auditability (ADR-041 D10).
- **Failure Handling:** If T1 REJECTED → `ExecutionIntent` transitions to terminal `RISK_REVALIDATION_REJECTED` state (D13); NO broker submission.
- **Policy Identity (D12):** T1 independently resolves the authoritative current account risk profile assignment server-side. T0 and T1 may use different policy versions — this is auditable information. Caller cannot select T1 policy.
- **Test Coverage:** Deterministic tests for T1 APPROVED/REJECTED scenarios, policy drift detection, stale-fact detection.

### Out of Scope

- Modifying T0 risk evaluation (`TradePlanRiskEvaluationService`).
- New risk rules or risk engine changes.
- Position close path (ADR-040: reducing exposure does not require entry RiskEvaluation).
- AI recommendations or scanner architecture.
- FTMO/cTrader/prop-firm integration.
- Partial close, SL/TP management.
- Frontend redesign (reuse existing Execute button flow).
- Automatic execution or autonomous trading.
- New persistent Position aggregate.

---

## Acceptance Criteria

* [ ] AC1: After human clicks Execute and `ExecutionIntent` is VALIDATED, a fresh T1 risk revalidation runs before any broker submission.
* [ ] AC2: T1 uses the same `RiskEngine` and evaluation mode as T0, but independently resolves the authoritative current account risk profile assignment (D12).
* [ ] AC3: T1 loads fresh authoritative facts (broker account, positions, market prices, equity, margin) at execution time.
* [ ] AC4: If T1 decision = APPROVED or APPROVED_WITH_WARNINGS → execution pipeline proceeds normally.
* [ ] AC5: If T1 decision = REJECTED → `ExecutionIntent` transitions to terminal `RISK_REVALIDATION_REJECTED` state (D13); NO broker command is prepared or submitted.
* [ ] AC6: T1 evaluation is persisted with decision, metrics, violations, timestamp, policy identity, and linkage to `ExecutionIntent` for auditability (ADR-041 D10).
* [ ] AC7: T0 and T1 evaluations remain separately queryable and distinguishable, including their respective policy identities.
* [ ] AC8: T1 revalidation timeout/unavailable → `RISK_REVALIDATION_UNAVAILABLE` (non-terminal, D14); NO broker submission; distinct from broker UNKNOWN (ADR-041 D6 distinction). Same intent retryable via explicit human action. Automatic/bounded retry forbidden in Story 0034.
* [ ] AC9: Existing Story 0030 execution flow (human authority, idempotency, UNKNOWN/reconciliation) unchanged.
* [ ] AC10: Position close (Story 0033) unaffected — operates under ADR-040 risk semantics.

---

## Constraints

- Preserve existing execution architecture (ADR-029 pipeline, `ExecutionIntent`/`ExecutionAttempt` lifecycle).
- Respect ADR-001 (human authority), ADR-014 (decision pipeline), ADR-029 (execution domain), ADR-030 (broker isolation), ADR-040 (position management), ADR-041 (financial command safety).
- Trading Core owns authoritative validation and T1 revalidation.
- T1 policy is resolved server-side by Trading Core; no caller may select the T1 policy (D12).
- Human validation cannot override Risk Domain decision (T0 or T1).
- Risk Domain never accesses Broker Service directly.
- Broker Service remains provider of facts and execution capabilities only.
- No broker order may result without explicit human action AND T1 approval.
- Deterministic risk evaluation: same inputs → same result.
- T1 must not introduce new dependencies on AI, scanners, or external services.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md` — Trading OS Vision (human authority)
- `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline (layered pipeline, human validation)
- `docs/architecture/adr/ADR-029.md` — Execution Domain Architecture (lifecycle, idempotency, reconciliation)
- `docs/architecture/adr/ADR-030.md` — Broker Service Architecture (broker isolation, no business logic)
- `docs/architecture/adr/ADR-040.md` — Position Management Command Architecture
- `docs/architecture/adr/ADR-041.md` — Human-Controlled Trade Execution and Financial Command Safety

---

## Relevant Modules

- `trading-core` — T1 revalidation service, `ExecuteTradeService` integration, `ExecutionIntent` state extension, persistence.
- `risk-domain` — `RiskEngine` reuse (no changes), `RiskEvaluationContextBuilder` reuse.
- `trading-os-web` — Angular execution flow (minimal: T1 loading state between Execute click and submission).

---

## Repository Baseline

```
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD = ad7a0e4825d4bec5020cff42df63cbff716cde12
WORKTREE = clean
STORY_0030_STATE = merged (PR #26)
STORY_0033_STATE = implemented, human-accepted (PR #29)
ADR_040_STATUS = Accepted
ADR_041_STATUS = Proposed (this ADR)
```

---

## Current Execution Flow (Repository Reality)

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

**Gap:** No risk revalidation between `ValidateAndCreateService` and `BrokerSubmissionStep`.

---

## Proposed T1 Integration Point

```
POST /executions/validate
    ↓
ValidateAndCreateService → ExecutionIntent (VALIDATED)
    ↓
*** NEW: T1 Revalidation ***
    ↓ resolve authoritative current account risk profile (D12)
    ↓ load fresh authoritative facts
    ↓ run RiskEngine with resolved policy + fresh facts
    ↓
if T1 APPROVED:
    POST /executions/{id}/execute → ExecuteTradeService pipeline
else:
    ExecutionIntent → terminal RISK_REVALIDATION_REJECTED (D13)
    NO execute call
```

**Rationale:** 
- `ExecutionIntent` already exists with immutable idempotency key and parameters.
- T1 can access `ExecutionIntent` for context (tradePlanRef, brokerAccountId, parameters).
- T1 failure leaves `ExecutionIntent` in terminal state — auditable, no broker call.
- Minimal pipeline disruption: `ExecuteTradeService` unchanged; gating occurs before it.

---

## Existing Components to Reuse

| Component | Location | Reuse For |
|-----------|----------|-----------|
| `RiskEngine` / `RiskEngines.standard()` | `risk-domain/engine/` | Deterministic T1 evaluation (same engine, potentially different policy version) |
| `TradePlanRiskEvaluationService` logic | `trading-core/risk/application/` | Fact loading, context building, provenance assembly |
| `BrokerRiskFactsPort` | `trading-core/risk/application/port/` | Fresh broker account/position snapshots |
| `MarketValuationPort` | `trading-core/risk/application/port/` | Fresh market prices |
| `RequiredMarginPort` | `trading-core/risk/application/port/` | Fresh margin requirements |
| `RiskPersistence` | `trading-core/risk/infrastructure/persistence/` | Resolve authoritative current account profile (D12), persist T1 |
| `ExecutionIntentRepositoryPort` | `trading-core/execution/domain/repository/` | Link T1 to ExecutionIntent |
| `ExecutionStatus` | `trading-core/execution/domain/valueobject/` | Add `RISK_REVALIDATION_REJECTED` terminal state (D13) |

---

## Components Likely Requiring Modification

| Component | Change |
|-----------|--------|
| `ExecuteTradeService` / `ExecutionController` | Gate `execute()` on T1 result (or new pre-execute step) |
| `ExecutionIntent` aggregate | Add `t1EvaluationId` reference, `RISK_REVALIDATION_REJECTED` status (D13) |
| `RiskPersistence` | New T1 evaluation persistence method (distinct from T0 `StoredEvaluation`) |
| `ExecutionPipelineContext` / `ExecutionValidationStep` | Potentially move T1 gate here as first pipeline step |
| Angular `ExecutionService` / `PlanPage` | Show T1 revalidation loading state |

---

## Non-Goals (Explicit)

- AI scanners / passive scanner / active scanner
- Position-monitoring agent
- Market Intelligence redesign
- Data Science / backtesting / vector database / RAG
- FTMO integration / cTrader integration
- New Kraken features
- Prop-firm-specific rules
- Automatic execution / autonomous trading
- SL/TP management / partial close
- Frontend redesign
- New persistent Position aggregate

---

## Definition of Done

* [ ] ADR-041 accepted per repository workflow
* [ ] T1 revalidation service implemented and tested
* [ ] T1 integrates at correct pipeline boundary (before broker submission)
* [ ] T1 independently resolves authoritative current account policy (D12)
* [ ] T1 REJECTED → no broker command, terminal intent state (D13)
* [ ] T1 timeout/unavailable → `RISK_REVALIDATION_UNAVAILABLE` (non-terminal, D14); same intent retryable via explicit human action; distinct from broker UNKNOWN
* [ ] T1 evaluation persisted with full audit linkage including policy identity (D15)
* [ ] T0 and T1 separately queryable with policy drift reconstructable
* [ ] Automated tests pass (deterministic T1 scenarios, policy drift scenarios)
* [ ] Angular shows T1 loading state
* [ ] No regression in Story 0030/0033 flows
* [ ] Architecture validation against ADR-001, ADR-014, ADR-029, ADR-030, ADR-040, ADR-041
* [ ] No out-of-scope functionality introduced

---

## Validation

* Trading Core tests (T1 revalidation service, integration with execution pipeline, T0/T1 policy resolution, policy drift scenarios, failure modes)
* Risk Domain tests (RiskEngine deterministic behavior with fresh facts)
* Angular tests (T1 loading state, T1 rejection UX)
* Angular production build
* Manual verification: Execute → T1 revalidation → broker submission flow
* Architecture validation against ADR-041 D1–D10

---

## Story Lifecycle

| Status | Value |
|---|---|
| IMPLEMENTATION_COMPLETE | NO |
| FINAL_SAFETY_REVIEW_COMPLETE | NO |
| HUMAN_ACCEPTANCE | NO |
| STORY_0034_ACCEPTED | NO |

---

## Open Design Decisions (Requiring Human Approval)

The following decisions are documented in the accompanying discovery report and MUST be resolved before implementation:

| Decision | Status |
|----------|--------|
| **D11** — T1 as mandatory server-side invariant | HUMAN_APPROVED |
| **D12** — Execution-time authoritative risk policy (D12) | HUMAN_APPROVED |
| **D13** — T1 rejection persistence (terminal intent state) | HUMAN_APPROVED |
| **D14** — T1 unavailable/timeout/stale facts semantics | HUMAN_APPROVED |
| **D15** — T1 audit persistence (dedicated T1 evaluation model) | HUMAN_APPROVED |

See discovery report for analysis of each.