# Story 0241 — Code Review

**Date:** 2026-11-14
**Reviewer:** Independent retrospective review
**Scope:** Story 0241 — Add Bounded Recurring Passive Dispatch
**Branch/commit:** merged via PR #293/#294 on `main`

---

## Verdict: APPROVED_WITH_NOTES

No correctness defects found. All acceptance criteria are verifiable from the code. Two risks and two observations are noted below.

---

## Findings

### RISK-01 — No unit test for `PassiveScanDispatchCoordinator.dispatchOnce()`

**Severity:** Risk (not defect)
**Location:** `application/passive/PassiveScanDispatchCoordinator.java`

The core dispatch logic — budget enforcement, concurrency check, ordering, no-work path, duplicate-prevention path — is covered only indirectly through the persistence integration test (`PassiveScanPersistenceTest`) and the properties unit test (`PassiveDispatchPropertiesTest`). No dedicated unit test drives `dispatchOnce()` with mocked dependencies and controlled inputs.

Affected story ACs: "Scheduler creates no more than the configured number of markets per run", "Concurrent passive executions never exceed the configured limit", "Repeated scheduler ticks do not duplicate in-flight logical work".

**Recommended mitigation:** Add a `PassiveScanDispatchCoordinatorTest` with mocked `PassiveMarketUniverseResolver`, `PassiveScanRunRepository`, `AnalysisExecutionService`, and controlled `Clock`. Targeted scenarios: empty universe → no work, `maxMarketsPerRun=2` with 3 eligible → 2 dispatched, active-market present → duplicate short-circuit, `enabled=false` → disabled result. Address in Story 0245.

---

### RISK-02 — Lease-expiry recovery path not tested end-to-end

**Severity:** Risk
**Location:** `JpaPassiveDispatchCapacityRepository.recoverExpired()`, `PassiveScanClaimService.recover()`

The production path for crash recovery is: claimed market → process restart → next scheduler tick → `recoverOrphanedCapacity()` releases the stale capacity slot → market recovers to REGISTERED. The persistence test covers `acquire` and `release` atomicity but does not test the full recovery path: a lease that has passed `expiresAt` is recovered and the slot freed. No integration test simulates a process interruption.

Affected story AC: "Lease expiry allows safe recovery after process interruption."

**Recommended mitigation:** Add an integration test with `@SpringBootTest` + H2/testcontainers: create a claimed market, advance the clock past `claimLease`, call `recoverOrphanedCapacity()`, assert the slot is free and the market returns to REGISTERED. Address in Story 0245.

---

### OBSERVATION-01 — Ineligible markets consume batch slots in `evaluateBounded`

**Location:** `PassiveScanDispatchCoordinator.evaluateBounded()` lines 212–224

Both eligible and ineligible entries are added to `selected`; the loop terminates when `selected.size() >= maxMarketsPerRun`. With `maxMarketsPerRun = 2`, if the two most-due markets are ineligible (closed, provider unavailable), zero eligible markets are dispatched in that tick. The eligible markets become the most-due on the next tick (ineligible ones have their `dueAt` advanced by `updatedAt + cadence`), so the effect is bounded.

This behavior is undocumented and not explicitly tested. It is a correct implementation of the resource-budget philosophy ("evaluate at most N markets per tick regardless of eligibility") but could surprise operators expecting that a 2-market batch always dispatches 2 eligible analyses.

**Recommended mitigation:** Document the behavior in a comment or the properties Javadoc. Add a scenario in Story 0245 fixture testing.

---

### OBSERVATION-02 — Implementation report delivery state is stale

**Location:** `docs/architecture/stories/0241-bounded-passive-dispatch/implementation-report.md` (last paragraph)

States: "Implementation changes are uncommitted and remain available for human review." This was correct at the time of writing but is now stale — the work has been merged via PR #293/#294. Update the delivery state accordingly.

---

## Acceptance Criteria Verification

| AC | Code evidence | Verdict |
|---|---|---|
| No more than configured markets per run | `evaluateBounded()` loop: `selected.size() < maxMarketsPerRun` | ✅ |
| No work created when no due market | `due.isEmpty() → noWork()` at line 91 | ✅ |
| Market due only when all checks pass | `isDue()` → `dueAt()` → cadence, retry, attempt, max checks | ✅ |
| Concurrent executions ≤ configured limit | `PassiveDispatchCapacityRepository.acquire()` with CHECK constraint | ✅ |
| Repeated ticks do not duplicate in-flight | Active-markets check at line 74; idempotency key based on fingerprint+bucket | ✅ |
| Lease expiry allows safe recovery | `recoverOrphanedCapacity()` + `recover()` path exists; production code correct | ✅ (no test — RISK-02) |
| Retries persist attempt count, cannot retry indefinitely | `maxAttempts` check at line 141; `Instant.MAX` on exhaustion | ✅ |
| Provider failures bounded, do not block other markets | `eligibilityReason()` exception → `PROVIDER_UNAVAILABLE` per-market | ✅ |
| Closed/unavailable markets → explicit non-positive outcome | `PassiveScanMarket.skipped()` created with reason | ✅ |
| Deterministic due-time ordering prevents starvation | `Comparator.comparing(dueAt).thenComparing(marketId).thenComparing(provider).thenComparing(productType)` | ✅ |
| Run reaches truthful terminal/partial state | `PassiveScanReconciliationService.aggregate()` logic | ✅ |
| Scheduler disabled unless explicitly enabled | `@ConditionalOnProperty(havingValue="true")` + `enabled=${...:false}` | ✅ |
| Metrics expose all required outcomes | `PassiveDispatchMetrics`: selected, skipped, dispatched, retry, duplicate, insufficientEvidence, providerBudget, leaseRecovery | ✅ |
| Tests cover budget, concurrency, retry, lease, restart | Properties test, persistence claim/capacity tests; coordinator unit tests absent (RISK-01) | ✅ partial |

---

## Safety Assessment

- Scheduler only registers analyses through `AnalysisExecutionService` — no broker, no TradePlan, no risk.
- ArchUnit (`PassiveToActiveArchitectureTest`) confirms `application.passive` cannot depend on `ActiveScanApplicationService`.
- Default `enabled=false` protects against accidental activation.
- No public endpoint added.

---

## Summary

The implementation correctly enforces all safety and resource boundaries. The bounded dispatch logic, durable capacity, idempotency, and retry budget are all correctly implemented. The two risks (missing coordinator unit tests and missing lease-recovery integration test) reduce confidence in correctness of the boundary conditions but do not indicate a current defect. Both should be addressed in Story 0245.
