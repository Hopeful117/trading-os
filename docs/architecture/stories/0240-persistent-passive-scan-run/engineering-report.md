# Story 0240 — Engineering Report

**Date:** 2026-11-14 (retrospective)
**Status:** Merged — PR #292 — independent code review complete

---

## 1. Summary

Story 0240 introduced the durable operational boundary for passive scanning: `PassiveScanRun` and
`PassiveScanMarket` with their full persistence lifecycle, idempotency guarantees, versioned
optimistic locking, and reconciliation service. The implementation is contained within
`market-intelligence`. No scheduler, public endpoint, broker operation, or ActiveScan change was
introduced.

---

## 2. Delivery Evidence

| Item | Evidence |
|---|---|
| Implementation | PR #292 |
| Merge commit | `c28a246d91bb0f1a77c7a053c63ea305cdb4aaef` |
| Flyway migration | `V29__passive_scan_run_lifecycle.sql` |
| Implementation Report | `implementation-report.md` (2026-11-13) |
| Test suite | Passed — `mvn verify` for `market-intelligence` |

---

## 3. Validation Results

```
mvn -q -Dtest=PassiveScanRunTest,PassiveScanMarketTest,PassiveScanPersistenceTest test  PASSED
mvn -q test    PASSED (full market-intelligence module)
mvn -q verify  PASSED (full market-intelligence module)
Flyway migration to V29: PASSED
git diff --check: PASSED
```

---

## 4. Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| One logical run key creates exactly one PassiveScanRun | ✅ | UNIQUE(idempotency_key) + application check |
| Retrying same request does not duplicate children | ✅ | `createIdempotently` + `DataIntegrityViolationException` recovery |
| Different fingerprint under same key rejected | ✅ | `requestFingerprint` comparison in `create()` |
| Every child preserves canonical market/provider/product identity | ✅ | Immutable `PassiveScanMarket` fields; persistence test |
| Run and child rows durable before dispatch possible | ✅ | Atomic `@Transactional create()` + FK constraint |
| Run states distinguish no work / partial / failure / success | ✅ | `COMPLETED_NO_WORK`, `PARTIALLY_COMPLETED`, `FAILED`, `COMPLETED` |
| Insufficient evidence not represented as successful | ✅ | Distinct `INSUFFICIENT_EVIDENCE` child status |
| Restart can reconstruct all unfinished state | ✅ | `findById` + `findMarketsByRunId` + `reconcile()` |
| Concurrent claims cannot duplicate logical children | ✅ | Versioned optimistic lock in `claim()` SQL |
| Persistence, idempotency, state-transition tests pass | ✅ | 3 test classes, all passing |

---

## 5. Architecture Notes

### Domain objects

`PassiveScanRun` and `PassiveScanMarket` are final, immutable value objects with explicit factory
methods (`requested`, `completedNoWork`, `registered`, `skipped`, `restore`) and `advanceTo()`
transition guards. Invalid transitions throw `IllegalStateException`. Both objects are
reconstructable from persistence via the `restore()` factory.

### Concurrency

Concurrent dispatch claims are prevented by a versioned optimistic-lock `UPDATE` in
`SpringDataPassiveScanMarketRepository.claim()`. Only the row matching `(id, expected_status,
expected_version, lease_expired_or_null)` is updated; all other concurrent attempts return 0.

### Idempotency

Request-level idempotency is two-layered:
1. `findByIdempotencyKey` pre-check with fingerprint comparison.
2. `DataIntegrityViolationException` catch-and-reload for concurrent first-time requests.

### Reconciliation

`PassiveScanReconciliationService.reconcile()` derives the run's terminal status from child
statuses without requiring in-memory state across a restart. All-SKIPPED → `COMPLETED_NO_WORK`;
any-COMPLETED → `COMPLETED` or `PARTIALLY_COMPLETED`; none-COMPLETED → `FAILED`.

---

## 6. Residual Risks

| Ref | Risk | Impact | Resolution |
|---|---|---|---|
| R-0240-1 | AC#3 fingerprint-conflict path has no application-level test | Low | Story 0245 Scenario H |
| R-0240-2 | No crash-restart cycle test | Low | Story 0245 Scenario G |
| R-0240-3 | V29 `analysis_execution_id NOT NULL` incompatible with SKIPPED markets; fixed in V31 | None (resolved) | V31 applied |

---

## 7. Outstanding Gates

| Gate | Status |
|---|---|
| Implementation complete | ✅ |
| Test suite passing | ✅ |
| Flyway migration validated | ✅ |
| PR merged | ✅ |
| Independent code review | ✅ (retrospective — 2026-11-14) |
| Human approval of review findings | ⚠️ Pending |
| Cross-service E2E validation | ⚠️ Story 0245 |
