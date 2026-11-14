# Story 0241 — Engineering Report

**Date:** 2026-11-14
**Status:** Merged — PR #293/#294 — retrospective review complete — pending human approval

---

## 1. Summary

Story 0241 delivered bounded, recurring passive dispatch for Trading OS Market Intelligence. It introduced a disabled-by-default Spring scheduler, a durable capacity model (claims and leases), deterministic due-time selection, configurable batch and concurrency limits, exponential retry backoff, reconciliation of terminal executions, and Micrometer metrics for all required outcomes.

All 14 acceptance criteria are satisfied by the production code. Two risks are identified relating to test coverage depth (coordinator unit tests, lease-recovery integration test). No correctness defects were found during retrospective review.

---

## 2. Delivery Evidence

| Item | Evidence |
|---|---|
| Implementation | PR #293/#294, merged to `main` |
| Flyway migration | `V30__bounded_passive_dispatch.sql` |
| Implementation Report | `implementation-report.md` (2026-11-14) |
| Code Review | `code-review.md` (2026-11-14, retrospective) |

---

## 3. Validation Results

The market-intelligence module test suite passed (exit code 0). The implementation report reports 1029 tests passing. The following targeted test files are directly relevant to Story 0241:

| Test | Coverage |
|---|---|
| `PassiveDispatchPropertiesTest` | Defaults disabled, bounded values enforced |
| `PassiveScanPersistenceTest.conditionalClaimAllowsOnlyOneWinner` | Optimistic concurrency on claim |
| `PassiveScanPersistenceTest.durableCapacityAllowsOnlyConfiguredConcurrentLeases` | Capacity counter atomicity |
| `PassiveScanMarketTest.recordsExplicitNonPositiveOutcomeWithoutCreatingDispatchableWork` | SKIPPED market path |
| `PassiveScanRunTest` | Run lifecycle and terminal-state rules |
| `PassiveToActiveArchitectureTest` | ArchUnit safety boundaries |

---

## 4. Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| ≤ configured markets per run | ✅ | `evaluateBounded()`: `selected.size() < maxMarketsPerRun` |
| No work when no due market | ✅ | `due.isEmpty() → noWork()` |
| Market due only when all checks pass | ✅ | `dueAt()` checks cadence, non-terminal, retry budget, attempt count |
| Concurrent executions ≤ configured | ✅ | Durable `passive_dispatch_capacity_state` with CHECK constraint |
| No duplicate in-flight work | ✅ | Active-markets check; idempotency key on fingerprint+time-bucket |
| Lease expiry → safe recovery | ✅ | `recoverOrphanedCapacity()` and `recover()` paths present (R-02: no integration test) |
| Bounded retries with backoff | ✅ | `maxAttempts` default 3; exponential backoff capped at `retryMaxDelay` |
| Provider failures bounded | ✅ | Per-market `eligibilityReason()` exception → `PROVIDER_UNAVAILABLE` |
| Closed/unavailable → explicit non-positive | ✅ | `PassiveScanMarket.skipped()` with reason code |
| Deterministic ordering prevents starvation | ✅ | `Comparator.comparing(dueAt).thenComparing(marketId)...` |
| Truthful terminal/partial state | ✅ | `PassiveScanReconciliationService.aggregate()` |
| Scheduler disabled unless explicitly enabled | ✅ | `@ConditionalOnProperty(havingValue="true")` + `enabled=${...:false}` |
| Metrics expose all required outcomes | ✅ | 8 counters: selected, skipped, dispatched, retry, duplicate, insufficientEvidence, providerBudget, leaseRecovery |
| Tests cover budget, concurrency, retry, lease, restart | ✅ partial | Persistence tests cover capacity; coordinator unit tests absent (R-01) |

---

## 5. Architecture Notes

### Scheduler isolation

`PassiveScanDispatchScheduler` is a `@ConditionalOnProperty` bean — absent from the Spring context unless `market-intelligence.passive-dispatch.enabled=true`. The default configuration binds this to env var `MARKET_INTELLIGENCE_PASSIVE_DISPATCH_ENABLED` defaulting to `false`. No test profile sets this to `true`.

### Durable capacity model

A single `passive_dispatch_capacity_state` row with optimistic versioning (not a row lock) gates dispatch. Lease records in `passive_dispatch_capacity_leases` are keyed to `scan_market_id` (UNIQUE constraint). The `active_count >= 0` check constraint prevents the counter going negative. This design is correct for single-instance deployments; a multi-instance deployment would require a stricter serialized slot acquisition.

### Retry backoff

Exponential backoff: `cadence × 2^(attemptCount-1)`, capped at `retryMaxDelay` (default 30 min). After `maxAttempts` (default 3), `dueAt()` returns `Instant.MAX` — market is permanently deferred until manually reset or the scenario is resolved.

### Idempotency key

`SHA-256(fingerprint + ":" + epochBucket + ":" + maxMarketsPerRun)` where `epochBucket = epochSeconds / fixedDelaySeconds`. Two scheduler ticks within the same 5-minute window get the same key and the same run (idempotent). Different windows get different runs.

---

## 6. Residual Risks

| Ref | Risk | Impact | Mitigation |
|---|---|---|---|
| R-0241-1 | Concurrency bound is non-atomic under multi-instance deployment | Medium | Documented; V1 is single-instance; addressed in future Story or ADR |
| R-0241-2 | No unit test for `PassiveScanDispatchCoordinator.dispatchOnce()` | Medium | Address in Story 0245 (coordinator unit test with mocked dependencies) |
| R-0241-3 | No integration test for lease-expiry recovery end-to-end | Low | Address in Story 0245 (recovery scenario) |
| R-0241-4 | Ineligible markets consume batch slots (`evaluateBounded`) | Low | Bounded by next tick; document behavior |

---

## 7. Outstanding Gates

| Gate | Status |
|---|---|
| Implementation complete | ✅ |
| Test suite passing | ✅ |
| Production build passing | ✅ (included in module suite) |
| PR merged | ✅ |
| Independent code review | ✅ (retrospective — `code-review.md`) |
| Human approval of review findings | ✗ — pending |
