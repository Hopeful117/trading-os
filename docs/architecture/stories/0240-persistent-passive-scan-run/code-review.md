# Story 0240 — Code Review

**Date:** 2026-11-14
**Reviewer:** Independent retrospective review
**Scope:** PR #292 — Persist the PassiveScanRun Lifecycle
**Verdict:** APPROVED_WITH_NOTES

---

## Findings

### RISK-1 (P2) — AC#3 fingerprint-conflict path not covered by application-level test

**File:** `PassiveScanRunApplicationService.java:61`

AC#3 states "A different universe fingerprint under the same logical key is rejected." The check
is correctly implemented:

```java
if (!existing.requestFingerprint().equals(fingerprint)) {
    throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
}
```

However, no test exercises this path at the application service level. The underlying DB uniqueness
constraint (`UNIQUE(idempotency_key)`) prevents silent duplicates, but the semantic conflict (same
key, different fingerprint) has no test. Story 0245 should include a deterministic fixture for this
case.

---

### RISK-2 (P2) — Restart-reconstruction scenario not explicitly tested

**File:** `PassiveScanPersistenceTest.java`

AC#8 states "Restart/reload can reconstruct all unfinished run and child state." The persistence
round-trip test covers the happy path (`findById`, `findMarketsByRunId`). The reconciliation
service (`PassiveScanReconciliationService`) correctly re-derives run status from child states.
However, there is no test that simulates a process restart with partially-complete markets and
verifies that dispatch can safely resume. This is a Story 0245 E2E scenario (Scenario G).

---

### OBS-1 — V29 schema incompatible with SKIPPED markets; fixed in V31

**File:** `V29__passive_scan_run_lifecycle.sql:36`, `V31__allow_passive_skipped_without_execution.sql`

V29 declared `analysis_execution_id UUID NOT NULL` in `passive_scan_markets`. The domain
`PassiveScanMarket.skipped()` factory explicitly sets `analysisExecutionId = null` (enforced by
the domain constructor guard). This made V29 incompatible with SKIPPED persisted markets. The gap
was fixed in V31 (`DROP NOT NULL`), delivered in Story 0243.

No impact on current correctness (V31 is applied). Recorded as a cross-story schema design gap.

---

### OBS-2 — INSUFFICIENT_EVIDENCE-only run classified as FAILED

**File:** `PassiveScanReconciliationService.java:48`

A run where all markets reach `INSUFFICIENT_EVIDENCE` (and none `COMPLETED`) is classified as
`FAILED` by `aggregate()`. This is consistent with AC#7 ("Insufficient evidence is not represented
as successful analysis") and the story's explicit out-of-scope list. It is an intentional design
choice, documented here for transparency. If a distinct `COMPLETED_INSUFFICIENT` state is ever
desired, it would require a new status value.

---

### OBS-3 — `saveRun` always writes version=0

**File:** `JpaPassiveScanRunRepository.java:54`

`saveRun` sets `entity.version = 0` unconditionally. This is safe because `saveRun` is only
called at initial run creation; all subsequent run state transitions go through
`transitionRunStatus` (a conditional `UPDATE`). If `saveRun` were called after initial creation,
it would silently reset the version. The pattern is correct but fragile; a defensive check or
documentation comment would remove the ambiguity.

---

## Acceptance Criteria Assessment

| AC | Impl | Test |
|---|---|---|
| One logical run key creates exactly one PassiveScanRun | ✅ UNIQUE constraint + idempotency check | ✅ DB constraint; no service-level test |
| Retrying the same run request does not duplicate child executions | ✅ `createIdempotently` + race recovery | ✅ Covered by idempotency key uniqueness |
| A different universe fingerprint under the same logical key is rejected | ✅ `requestFingerprint` comparison | ⚠️ No test (RISK-1) |
| Every child preserves canonical market/provider/product identity | ✅ Immutable fields in domain + DB | ✅ `persistsAndReloads...` |
| Run and child rows are durable before dispatch is possible | ✅ `@Transactional` creation + FK constraint | ✅ Persistence test |
| Run states distinguish no work, partial completion, failure and success | ✅ `COMPLETED_NO_WORK`, `PARTIALLY_COMPLETED`, `FAILED`, `COMPLETED` | ✅ `advancesThroughDurableLifecycle` |
| Insufficient evidence is not represented as successful analysis | ✅ `INSUFFICIENT_EVIDENCE` distinct status | ✅ `recordsExplicit...` |
| Restart/reload can reconstruct all unfinished run and child state | ✅ `findById` + `findMarketsByRunId` + reconcile | ⚠️ Round-trip only; no crash-restart scenario (RISK-2) |
| Concurrent claims cannot create duplicate logical children | ✅ Versioned optimistic lock in `claim()` | ✅ `conditionalClaimAllowsOnlyOneWinner` |
| Persistence, idempotency and state-transition tests pass | ✅ All 3 test classes pass | ✅ Confirmed via implementation report |

---

## Summary

The implementation is structurally sound. Domain objects are immutable and use explicit factory
methods with transition guards. Concurrency is handled via versioned optimistic locking and a
durable capacity table. The two notable gaps (fingerprint-conflict test, crash-restart scenario)
are both addressed by Story 0245. No P0 or P1 defects found.
