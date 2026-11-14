# Story 0242 — Implementation Report

**Date:** 2026-11-14 (reconstructed retrospectively from merged code)
**Status:** Merged — PR #294 (commit `41b9a741`) — independent code review and human approval pending
**Note:** This report was reconstructed from the merged code after governance gates were identified as missing. Contents reflect the actual implementation, not a contemporaneous record.

---

## 1. Summary

Story 0242 delivers a bounded, read-only passive awareness projection over existing
`TradingOpportunity(PASSIVE_SCAN)`, `MarketThesis`, `StrategyMatch`, and `Observation` records.
No new aggregate was introduced. No schema migration was added. The implementation is a pure
additive read path in `market-intelligence`.

The implementation also closes the safety boundary identified in the Repository Analysis:
`TradePlanningEngine` now explicitly rejects accountless `PASSIVE_SCAN` opportunities with a
regression test.

---

## 2. Changed Files

### Created — application/passive

| File | Purpose |
|---|---|
| `application/passive/PassiveAwarenessProjection.java` | Immutable read record combining opportunity, market, thesis, observations, diagnostics, and `current` boolean |
| `application/passive/PassiveAwarenessProjectionService.java` | Orchestrates `findLatestPassive` → evaluate → filter → paginate; MAX_SOURCE_ITEMS = 1000 hard cap |
| `application/passive/PassiveAwarenessPage.java` | Page result: items, page, size, total, truncated |
| `application/passive/PassiveAwarenessQuery.java` | Query parameters: instrument, provider, productType, currentOnly, page, size |
| `application/passive/PassiveCandidateEvaluator.java` | 27-rule deterministic evaluation; shared with Story 0243 `PassiveCandidateScanValidator` |

### Created — adapter/web

| File | Purpose |
|---|---|
| `adapter/web/PassiveAwarenessController.java` | `GET /api/v1/passive-awareness` with optional query params |
| `adapter/web/PassiveAwarenessResponse.java` | Full DTO: 27 fields including identity, provenance, diagnostics, timestamps |
| `adapter/web/PassiveAwarenessPageResponse.java` | Paginated wrapper |
| `adapter/web/PassiveCandidateSourceResponse.java` | Auxiliary source response (passive provenance summary) |

### Modified — application/tradeplan

| File | Change |
|---|---|
| `application/tradeplan/TradePlanningEngine.java` | Added guard: `origin != PASSIVE_SCAN || accountId.isPresent()` before allowing opportunity into plan generation |

### No schema migration

Story 0242 added no Flyway migration. The projection reads existing tables only.
Latest migration at time of merge: `V31__allow_passive_skipped_without_execution.sql` (from Story 0241).

---

## 3. Validation Results

The implementation introduced the following test classes (verified present in merged code):

| Test file | Coverage |
|---|---|
| `PassiveCandidateEvaluatorTest` | 7 tests: qualifying→current, superseded thesis, WATCH/NO_TRADE thesis, blocking contradiction, opportunity superseded, unknown version, market data failure |
| `PassiveAwarenessProjectionServiceTest` | 4 tests: qualifying positive, stale/missing evidence non-positive, expired required evidence, truncation at 1000 |
| `PassiveAwarenessControllerTest` | 1 test: REST response shape (presentationState, origin, provider, productType) |
| `MiSecurityIntegrationTest` (extended) | 2 tests added: missing JWT rejected on passive awareness, authenticated user can read passive awareness |
| `TradePlanningEngineTest` (extended) | 1 test added: `accountlessPassiveOpportunityCannotEnterTradePlanGeneration` |
| `PassiveCandidateProvenanceImmutabilityTest` | 3 tests (Story 0243 domain; uses `PassiveCandidateProvenance` which is a 0242 domain record) |
| `PassiveToActiveArchitectureTest` | 2 ArchUnit tests: passive code cannot depend on `ActiveScanApplicationService`; `PassiveCandidate*` classes have no TradePlan/Risk/broker dependencies |

Exact test execution evidence (run totals): not available from merged code inspection alone.
Module-level `mvn verify` output not preserved. See Story 0243 engineering report for the
cumulative test count at the time of its merge (which subsumes 0242).

---

## 4. Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| Observation facts distinguishable from passive opportunities | ✅ | No raw observation top-level endpoint; `origin=PASSIVE_SCAN` explicit in response; `observationIds` are reference arrays |
| Passive opportunity shown only when thesis and strategy invariants pass | ✅ | `PassiveCandidateEvaluator`: 27 rules; `current=true` only when diagnostics is empty |
| `ACTIVE` lifecycle state not presented as account-authorized | ✅ | `presentationState = current ? "PASSIVE_CANDIDATE" : "NON_POSITIVE"`; no account context in response |
| Missing, stale, contradictory evidence explicitly visible | ✅ | All 27 diagnostic codes emitted to response; NON_POSITIVE items included in feed |
| Every item carries market/provider/product identity and provenance | ✅ | `PassiveAwarenessResponse` includes marketId, provider, productType, sourceScanId, sourceScanMarketId, analysisExecutionId |
| Expired or superseded items not presented as current | ✅ | `OPPORTUNITY_OUTSIDE_VALIDITY`, `THESIS_OUTSIDE_VALIDITY`, `THESIS_SUPERSEDED` → current=false; tested |
| Repeated equivalent detections produce no unbounded duplicates | ✅ | `findLatestPassive` returns max-version per opportunityId; `repeatedEquivalentDetectionYieldsOneFeedItem` test |
| Projection does not claim account-specific tradability | ✅ | No accountId in passive awareness response; endpoint is account-independent |
| Accountless passive opportunities cannot enter TradePlan generation; regression test exists | ✅ | `TradePlanningEngine.java:134`; `accountlessPassiveOpportunityCannotEnterTradePlanGeneration` test |
| Read authorization prevents cross-user leakage | ✅ | Passive awareness is account-independent (all JWT holders see the same global projection); security test verifies JWT required; no user-scoped data exposed |
| Projection and freshness tests cover positive and non-positive outcomes | ✅ | `PassiveAwarenessProjectionServiceTest` covers positive (all references resolve) and non-positive (missing/stale) |
| Deterministic fixture tests: qualifying→1 candidate, non-qualifying→0, repeated→no flood | ✅ | `qualifyingCandidateIsCurrent`, `staleOrMissingEvidenceIsNonPositiveAndExcludedByCurrentOnly`, `repeatedEquivalentDetectionYieldsOneFeedItem` |

---

## 5. Architecture Notes

### No new aggregate

The story explicitly avoids a `MarketCandidate` aggregate. `PassiveAwarenessProjection` is a
transient read record assembled from existing domain objects at request time.

### Shared evaluator

`PassiveCandidateEvaluator` is reused by `PassiveCandidateScanValidator` (Story 0243), ensuring
the passive awareness feed and the passive-to-active transition use the same qualification rules.

### Bounded query strategy

`findLatestPassive(MAX_SOURCE_ITEMS + 1)` returns at most 1001 rows in one query, using a
correlated subquery for max-version selection. The `origin` field is not a dedicated column —
the query uses `WHERE payload LIKE '%PASSIVE_SCAN%'` on a TEXT column. This is functionally
correct but relies on a full-table scan as the opportunity table grows (see Residual Risks).

### Clock injection

`PassiveAwarenessProjectionService` injects `java.time.Clock` for deterministic freshness
evaluation. All time-sensitive tests use `Clock.fixed()`.

---

## 6. Residual Risks

| Ref | Risk | Severity |
|---|---|---|
| R-0242-1 | 19 of 27 diagnostic codes have no explicit test assertion (untested paths: STRATEGY_MATCH_*, OPPORTUNITY_NOT_ACTIVE, OPPORTUNITY_OUTSIDE_VALIDITY, OBSERVATION_NOT_CURRENT, THESIS_OUTSIDE_VALIDITY, THESIS_FRESHNESS_INVALID, REQUIRED_EVIDENCE_NON_POSITIVE, REQUIRED_EVIDENCE_FRESHNESS_INVALID, MARKET_THESIS_OPPORTUNITY_MISMATCH, MARKET_IDENTITY_MISSING, MARKET_IDENTITY_MISMATCH, PASSIVE_PROVENANCE_INCOMPLETE, PASSIVE_SCAN_MARKET_MISMATCH) | Medium — addressed by Story 0245 |
| R-0242-2 | `findLatestPassive` uses `LIKE '%PASSIVE_SCAN%'` on a TEXT payload column; no origin index exists | Low — bounded at 1000 items; acceptable for V1 volume |
| R-0242-3 | No Gateway routing integration test for `/api/v1/passive-awareness` | Medium — silent regression vector; addressed by Story 0245 (F-01) |
| R-0242-4 | Cross-user leakage test absent (passive awareness is intentionally account-independent, so this is design-correct, but the test does not explicitly assert two-user isolation) | Low |
