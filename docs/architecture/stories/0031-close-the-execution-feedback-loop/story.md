# Story 0031 — Close the Execution Feedback Loop

## Goal

After explicitly submitting a trade for execution, the trader can observe the resulting execution lifecycle—broker acknowledgement, fills when available, failure reasons, uncertain outcomes, and reconciliation progress—without reloading the page or guessing what happened.

---

## Context

Story 0030 completed the human-controlled execution path. The backend persists rich execution state across four tables. The frontend currently displays only status, ID, and timestamps. The trader cannot reliably answer: "What happened to the execution I just requested?"

---

## Problem

After execution submission, the frontend shows only execution status, ID, and timestamps. Missing feedback includes broker order reference, broker order status, fill information, failure reason, status refresh, UNKNOWN state handling, and retry action.

---

## Scope

### In scope

- Enrich ExecutionDto with broker order details and fills summary
- Enrich ExecutionDto with failure reason
- Add short-lived polling for non-terminal execution states
- Add UNKNOWN state handling (explanation, no blind retry)
- Add FAILED state handling (failure reason, retry button)
- Add human-readable status labels
- Add user-scoped POST /executions/{id}/reconcile endpoint

### Out of scope

- Positions page, position monitoring, PnL, stop/TP management
- Execution history page, cancel execution UI
- Dashboard account selection persistence
- WebSocket/SSE/Kafka for execution feedback
- Scheduled/background recovery

---

## Acceptance Criteria

* [ ] AC1: ExecutionDto includes brokerExternalOrderId, brokerOrderStatus, filledQuantity, averageFillPrice, totalFees, failureReason when available.
* [ ] AC2: When execution status is non-terminal, the Plan page periodically queries GET /executions/{id} until a stop condition is reached.
* [ ] AC3: Polling stops when status is terminal (COMPLETED, CANCELLED, EXPIRED) or when retryable FAILED is displayed.
* [ ] AC4: Polling stops after 5 minutes if status remains non-terminal.
* [ ] AC5: Transient HTTP errors do not display as execution failure.
* [ ] AC6: Polling stops when the component is destroyed.
* [ ] AC7: SUBMISSION_OUTCOME_UNKNOWN displays an explanation and offers reconciliation, not retry.
* [ ] AC8: FAILED displays failure reason and offers retry when backend permits.
* [ ] AC9: Human-readable status labels used instead of raw enums.
* [ ] AC10: Global /executions/recovery endpoint NOT exposed through Gateway.
* [ ] AC11: Relevant tests pass. No unrelated behavior changed.

---

## Constraints

* Preserve existing execution architecture (ADR-029).
* Respect ADR-001 (human authority), ADR-014 (decision pipeline).
* Trading Core remains authoritative for execution state, ownership, retry eligibility.
* Do not expose raw Kraken responses.
* Do not flatten UNKNOWN into FAILED.
* Do not introduce automatic execution.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` — Trading OS Vision
* `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline
* `docs/architecture/adr/ADR-029.md` — Execution Domain Architecture

---

## Relevant Modules

* `trading-core` — Enrich ExecutionDto, add user-scoped reconcile endpoint
* `trading-os-web` — Polling, enriched display, UNKNOWN/FAILED handling, retry action

---

## Definition of Done

* [ ] ExecutionDto enriched with broker order details and failure reason
* [ ] Fill aggregation computed on backend
* [ ] User-scoped reconciliation endpoint added
* [ ] Angular polling implemented
* [ ] UNKNOWN/FAILED/RECOVERY_BLOCKED states handled safely
* [ ] Human-readable status labels
* [ ] Automated tests pass
* [ ] Implementation report created

---

## Validation

* Trading Core tests (DTO projection, reconciliation endpoint, ownership)
* Angular tests (polling, enriched display, state handling)
* Angular production build
* Architecture validation against ADR-001, ADR-014, ADR-029
