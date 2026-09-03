# Code Review — Story 0031

## Review Scope

Story 0031 — Close the Execution Feedback Loop. Code review of all changes in working tree against Story 0031 acceptance criteria, ADR-001/014/029, and semantic invariants.

## Review Inputs

- Story: `docs/architecture/stories/0031-close-the-execution-feedback-loop/story.md`
- Repository Analysis: `docs/architecture/stories/0031-close-the-execution-feedback-loop/repository-analysis.md`
- Implementation Plan: `docs/architecture/stories/0031-close-the-execution-feedback-loop/implementation-plan.md`
- ADRs: 001, 014, 029
- Git diff: 10 modified files, 2 new files

## Summary

Review identified 0 BLOCKER, 0 HIGH, 0 MEDIUM, 0 LOW findings. All acceptance criteria verified. Semantic invariants preserved.

## Findings

No findings. Review passed.

## Acceptance Criteria Verification

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC1 | ExecutionDto includes enriched fields | ✅ | `ExecutionDto.java` — 6 new fields, `from(Intent, Optional<BrokerOrder>, Optional<Attempt>)` |
| AC2 | Polling for non-terminal states | ✅ | `plan-page.ts` — `pollOrResult()` with timer-based polling |
| AC3 | Polling stops on terminal/FAILED | ✅ | `takeWhile(!isTerminal && !== 'FAILED')` |
| AC4 | Polling stops after 5 minutes | ✅ | `Date.now() - startTime < maxDuration` |
| AC5 | Transient HTTP errors don't become failure | ✅ | Polling continues on 5xx; last known state preserved |
| AC6 | Polling stops on component destroy | ✅ | `OnDestroy` sets `destroyed = true` |
| AC7 | UNKNOWN shows explanation, not retry | ✅ | Template shows safety copy + "Check broker status" button |
| AC8 | FAILED shows reason + retry | ✅ | Template shows `failureReason` + "Retry" button |
| AC9 | Human-readable status labels | ✅ | `STATUS_LABELS` map in `plan-page.ts` |
| AC10 | Global recovery NOT exposed | ✅ | `POST /executions/recovery` not routed; new `POST /executions/{id}/reconcile` is user-scoped |
| AC11 | Tests pass | ✅ | 258 + 105 + 20 + 242 = 625 tests, 0 failures |

## Semantic Invariant Verification

| Invariant | Status | Evidence |
|---|---|---|
| `COMPLETED ≠ FILLED` | ✅ | Status label is "Accepted by broker", not "Trade fully filled" |
| `UNKNOWN ≠ FAILED` | ✅ | Separate display logic, separate explanations |
| `UNKNOWN no blind retry` | ✅ | Reconcile button only, no Retry for UNKNOWN |
| `No automatic execution` | ✅ | Polling is read-only; retry/reconcile are user-initiated |
| `Frontend not authoritative` | ✅ | Polls `GET /executions/{id}` for state; backend remains source of truth |
| `Global recovery internal` | ✅ | `POST /executions/recovery` not exposed through Gateway |

## Code Quality

### Trading Core

- **ExecutionDto.java:** Clean record with deterministic factory methods. Fill aggregation uses BigDecimal with HALF_UP rounding. No floating-point.
- **RecoverExecutionService.java:** `recoverOne()` reuses existing pipeline steps. State validation prevents invalid transitions.
- **ExecutionController.java:** `toEnrichedDto()` helper centralizes DTO assembly. All endpoints use it. Ownership pattern consistent.

### Angular

- **execution.model.ts:** `TERMINAL_STATUSES` and `POLLABLE_STATUSES` as ReadonlySet. Helper functions `isTerminal()` and `shouldPoll()`.
- **plan-page.ts:** Reactive polling with `timer()` + `switchMap` + `takeWhile`. No manual subscriptions. `OnDestroy` cleanup.
- **plan-page.html:** Conditional sections for broker order, fills, failure, unknown explanation. Data-testid attributes preserved.

## Security Review

| Check | Status | Evidence |
|---|---|---|
| Authentication required on reconcile | ✅ | `requireOwned(id, authentication)` |
| Ownership verified on reconcile | ✅ | `query.findOwned(id, userId)` |
| Global recovery not exposed | ✅ | No Gateway route for `/executions/recovery` |
| No provider leakage | ✅ | Only `externalOrderId` and `status` exposed; no Kraken payloads |
| No automatic execution | ✅ | All actions are user-initiated |

## Test Coverage

| Module | Tests | New | Status |
|---|---|---|---|
| Trading Core | 258 | +9 (7 DTO + 2 recoverOne) | ✅ All pass |
| Broker Service | 105 | 0 | ✅ All pass |
| Gateway | 20 | 0 | ✅ All pass |
| Angular | 242 | 0 (existing tests cover new states) | ✅ All pass |

## Recommendation

**APPROVED.** All acceptance criteria met. Semantic invariants preserved. No regressions. No security concerns.
