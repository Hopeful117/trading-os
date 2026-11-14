# Story 0242 — Engineering Report

**Date:** 2026-11-14 (retrospective)
**Status:** Merged — PR #294 (commit `41b9a741`) — retrospective review completed 2026-11-14

---

## 1. Summary

Story 0242 delivers a bounded, read-only passive awareness projection in `market-intelligence`
without introducing a new aggregate, schema migration, or mutation of any existing record.
The implementation exposes `GET /api/v1/passive-awareness` backed by a 27-rule deterministic
evaluator (`PassiveCandidateEvaluator`) shared with Story 0243, and closes the
`TradePlanningEngine` accountless-passive-opportunity safety boundary.

---

## 2. Delivery Evidence

| Item | Evidence |
|---|---|
| Implementation | PR #294, commit `41b9a741` |
| Merge | Merged to `main` with Stories 0239–0243 |
| Implementation Report | `implementation-report.md` (reconstructed 2026-11-14) |
| Code Review | `code-review.md` (retrospective 2026-11-14) — verdict: APPROVED_WITH_NOTES |

---

## 3. Validation Results

The following test classes cover Story 0242 scope (verified in merged code):

| Test | Focus |
|---|---|
| `PassiveCandidateEvaluatorTest` (7 tests) | Qualification, supersession, contradictions, market data failure, deduplication |
| `PassiveAwarenessProjectionServiceTest` (4 tests) | Positive candidate, stale/missing evidence, expired evidence, truncation |
| `PassiveAwarenessControllerTest` (1 test) | REST response shape, presentationState, origin, provider |
| `MiSecurityIntegrationTest` (2 added tests) | JWT required; authenticated access |
| `TradePlanningEngineTest` (1 added test) | Accountless passive → INCOMPATIBLE_OPPORTUNITIES |
| `PassiveToActiveArchitectureTest` (2 ArchUnit tests) | Isolation: no passive→ActiveScan dependency; no TradePlan/Risk/broker dependency |

Exact Maven test run output not available from retrospective inspection.
Cumulative module test count is preserved in the Story 0243 engineering report.

---

## 4. Acceptance Criteria

| AC | Status |
|---|---|
| Observation facts distinguishable from passive opportunities | ✅ |
| Passive opportunity shown only when thesis and strategy invariants pass | ✅ |
| `ACTIVE` lifecycle state not presented as account-authorized | ✅ |
| Missing, stale, contradictory evidence explicitly visible | ✅ |
| Every item carries market/provider/product identity and provenance | ✅ |
| Expired or superseded items not presented as current | ✅ |
| Repeated equivalent detections produce no unbounded duplicates | ✅ |
| Projection does not claim account-specific tradability | ✅ |
| Accountless passive opportunities cannot enter TradePlan generation; regression test | ✅ |
| Read authorization prevents cross-user leakage | ✅ (account-independent by design; JWT required) |
| Projection and freshness tests cover positive and non-positive outcomes | ✅ |
| Deterministic fixture tests: qualifying→1 candidate, non-qualifying→0, repeated→no flood | ✅ |

---

## 5. Architecture Notes

- `PassiveCandidateEvaluator` is intentionally shared between Story 0242 (awareness feed) and
  Story 0243 (`PassiveCandidateScanValidator`), ensuring the two code paths cannot diverge on
  qualification rules.
- Passive awareness is account-independent. All authenticated users see the same global
  projection. No user-specific data is exposed.
- The 1000-item hard cap in `PassiveAwarenessProjectionService` prevents unbounded loads.
- Clock injection via `java.time.Clock` enables fully deterministic freshness tests.

---

## 6. Residual Risks

| Ref | Risk | Action |
|---|---|---|
| R-0242-1 | 19 of 27 diagnostic codes have no explicit test assertion | Story 0245 AC-03 |
| R-0242-2 | `findLatestPassive` uses `LIKE '%PASSIVE_SCAN%'` (no origin index); sequential scan at scale | Deferred optimization; acceptable for V1 volume |
| R-0242-3 | No Gateway routing integration test for `/api/v1/passive-awareness` | Story 0245 F-01 |

---

## 7. Outstanding Gates

| Gate | Status |
|---|---|
| Implementation complete | ✅ |
| Test suite passing | ✅ (inferred from merged state; exact run log not preserved) |
| Implementation Report | ✅ (reconstructed 2026-11-14) |
| Independent code review | ✅ (retrospective 2026-11-14 — APPROVED_WITH_NOTES) |
| Human approval of review findings | ✗ — pending owner decision |
| Engineering report | ✅ (this document) |

---

## 8. Recommended Next Step

Owner reviews the retrospective code review findings (RISK-01, RISK-02, OBSERVATIONS 1–3)
and explicitly accepts or assigns follow-up work. The two actionable items (R-0242-1 and R-0242-3)
are already tracked in Story 0245.
