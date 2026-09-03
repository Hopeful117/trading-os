# Engineering Report — Story 0031

## Story

0031 — Close the Execution Feedback Loop

**Date**: 2026-09-03
**Branch**: `main` (working tree changes, not yet committed)
**HEAD**: `14fd772` → working tree changes

## Executive Summary

Story 0031 closes the execution feedback loop. After a human explicitly submits a trade for execution, the trader can now observe the resulting execution lifecycle without reloading the page or guessing what happened.

Before this Story, the backend persisted rich execution state across four tables (`execution_intent`, `execution_attempt`, `execution_broker_order`, `execution_event`), but the frontend displayed only status, ID, and timestamps. The trader could not reliably answer: "What happened to the execution I just requested?"

The implementation enriches the `ExecutionDto` with broker order details, fill aggregation, and failure reasons. It adds short-lived polling for non-terminal states. It introduces a safe, user-scoped reconciliation endpoint. It handles UNKNOWN, FAILED, and RECOVERY_BLOCKED states with appropriate safety semantics.

**Key semantic invariant preserved:** `ExecutionIntent.COMPLETED` means "Broker accepted the execution request." It does NOT mean the order is fully filled. The UI displays "Accepted by broker" — not "Trade fully filled."

---

## Original Problem

After Story 0030 connected the execution path, the frontend showed only:

```
Status: COMPLETED
Execution ID: ...
Created: ...
Updated: ...
```

The trader could not determine:

1. **Broker order reference** — which order did the broker create?
2. **Broker order status** — is it acknowledged, partially filled, filled?
3. **Fill information** — what quantity was filled at what price?
4. **Failure reason** — why was the order rejected?
5. **Status refresh** — has the status changed since I last looked?
6. **Unknown outcome** — did the broker accept my order?
7. **Retry action** — can I retry a failed execution?

---

## Architecture Before

```
ExecutionDto:
  id, tradePlanId, tradePlanVersion, riskEvaluationId,
  idempotencyKey, brokerAccountId, status,
  createdAt, updatedAt, expiresAt, version

Angular PlanPage:
  executionSubmitting → executionResult (frozen)

No polling. No retry. No reconcile. No broker details. No fills.
```

## Architecture After

```
ExecutionDto:
  id, tradePlanId, tradePlanVersion, riskEvaluationId,
  idempotencyKey, brokerAccountId, status,
  createdAt, updatedAt, expiresAt, version,
  brokerExternalOrderId, brokerOrderStatus,        ← NEW
  filledQuantity, averageFillPrice, totalFees,      ← NEW
  failureReason                                      ← NEW

Angular PlanPage:
  executionSubmitting → executionPolling → executionResult
                         ↓
                    GET /executions/{id} (timer-based)

  Actions: Retry (FAILED), Check broker status (UNKNOWN/RECOVERY_BLOCKED)
```

---

## Implementation Details

### Backend: Enriched ExecutionDto

The `ExecutionDto` record gained 6 new fields. The `from()` factory method now accepts `Optional<BrokerOrder>` and `Optional<ExecutionAttempt>` to compute:

- **Fill aggregation:** `filledQuantity = Σ fill.quantity`, `averageFillPrice = weighted average`, `totalFees = Σ fill.fee`
- **Failure mapping:** Deterministic switch on `resultCode` → human-readable string
- **Broker order projection:** `externalOrderId` and `status` from `BrokerOrder`

### Backend: User-Scoped Reconciliation

New endpoint `POST /executions/{id}/reconcile`:

- Authentication required
- Ownership verified via `requireOwned()`
- Only eligible states: `SUBMISSION_OUTCOME_UNKNOWN`, `RECONCILIATION_IN_PROGRESS`, `RECOVERY_BLOCKED`
- Reuses existing `RecoverExecutionService` pipeline
- Returns enriched `ExecutionDto`

The global `POST /executions/recovery` endpoint remains internal — NOT exposed through Gateway.

### Frontend: Polling

Short-lived polling using RxJS `timer()` + `switchMap` + `takeWhile`:

- **Start:** When execution status is non-terminal after initial response
- **Interval:** 2s for first 30s, then 5s
- **Stop:** Terminal status, FAILED, 5 minutes elapsed, or component destroyed
- **Error handling:** Transient HTTP errors (5xx) don't display as failure; last known state preserved

### Frontend: State Handling

| State | Display | Action |
|---|---|---|
| COMPLETED | "Accepted by broker" + broker order details | — |
| FAILED | "Execution failed" + failure reason | Retry button |
| SUBMISSION_OUTCOME_UNKNOWN | "Submission outcome uncertain" + safety explanation | "Check broker status" button |
| RECONCILIATION_IN_PROGRESS | "Checking broker status" | — |
| RECOVERY_BLOCKED | "Unable to confirm broker outcome" + safety explanation | "Check broker status" button |
| Partials/fills | Fill summary: quantity, average price, fees | — |

---

## Semantic Decisions

### COMPLETED ≠ FILLED

`ExecutionIntent.COMPLETED` means "Broker accepted the execution request." The `BrokerOrder` may later receive fills via `addFill()`, transitioning through `ACKNOWLEDGED → PARTIALLY_FILLED → FILLED`.

The UI displays "Accepted by broker" — not "Trade fully filled."

### UNKNOWN ≠ FAILED

`SUBMISSION_OUTCOME_UNKNOWN` means "submission outcome cannot be established." The broker may have accepted the order. Blind retry could risk duplicate execution.

The UI shows "Check broker status" (reconcile) — not "Retry."

### Global Recovery Internal

`POST /executions/recovery` processes ALL recoverable executions across ALL users. It has no authentication at the controller level. It must NOT be exposed through Gateway.

The new `POST /executions/{id}/reconcile` is user-scoped with ownership verification.

---

## Test Results

| Module | Command | Passed | Failed |
|---|---|---|---|
| Trading Core | `mvn test` | 258 | 0 |
| Broker Service | `mvn test` | 105 | 0 |
| Gateway | `mvn test` | 20 | 0 |
| Angular | `npm run test:ci` | 242 | 0 |
| Angular Build | `npx ng build` | OK | 0 |
| **Total** | | **625** | **0** |

---

## Files Changed

```
Trading Core (6 files):
  execution/api/ExecutionController.java
  execution/api/dto/ExecutionDto.java
  execution/application/service/RecoverExecutionService.java
  execution/infrastructure/configuration/ExecutionConfiguration.java
  execution/ExecutionPipelineTest.java (test)
  execution/api/dto/ExecutionDtoTest.java (test)

Angular (4 files):
  core/models/execution.model.ts
  core/services/execution.service.ts
  features/trade-planning/plan-page/plan-page.ts
  features/trade-planning/plan-page/plan-page.html

Documentation (3 files):
  docs/.../0031-.../story.md
  docs/.../0031-.../implementation-report.md
  docs/.../0031-.../repository-analysis.md
  docs/.../0031-.../implementation-plan.md
  docs/.../0031-.../code-review.md
  docs/.../0031-.../engineering-report.md
```

---

## Remaining Gaps

Intentionally out of scope:

- Execution history page
- Cancel execution UI
- WebSocket/SSE execution events
- Scheduled automatic recovery
- ExecutionDto with instrument/side/quantity from parameters

---

## Known Limitations

1. **Polling adds HTTP calls.** Mitigated by 2s→5s cadence and 5min max duration.
2. **Fill aggregation is summary-only.** Individual fills not exposed in this Story. Future detail view can use existing `BrokerOrder.fills()`.
3. **No execution history page.** `GET /executions` exists but no UI. Future Story.

---

## Recommendation

Story 0031 is **COMPLETE**. All acceptance criteria met. Semantic invariants preserved. 625 tests pass. No regressions. No security concerns. Changes ready for human review and commit.
