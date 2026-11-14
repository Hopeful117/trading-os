# Story 0245 — Implementation Report

**Date:** 2026-11-14
**Status:** Implementation complete — awaiting code review and human commit

---

## 1. Summary

Story 0245 adds deterministic integration tests validating the Passive Market Scanning V1
pipeline end-to-end. All tests use the existing H2 Testcontainers-equivalent infrastructure
(H2 in-memory with full Flyway migration). The passive scheduler is never activated; test logic
calls repositories and services directly.

---

## 2. Changed Files

| File | Change |
|---|---|
| `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/passive/PassiveScanV1ProjectionIntegrationTest.java` | **Created** — 6 integration tests covering Scenarios A, B, C, D, G, H |
| `gateway/src/test/java/com/hope/trading/gateway/GatewayDownstreamRoutingIntegrationTest.java` | **Modified** — added 2 tests for the `/api/v1/passive-awareness` route (F-01) |

---

## 3. Validation Results

### market-intelligence

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### gateway

```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 4. Acceptance Criteria Coverage

| AC | Story 0245 Requirement | Test | Status |
|---|---|---|---|
| AC-01 | Qualifying market → PASSIVE_CANDIDATE, current=true | `qualifyingPassiveScanOpportunityAppearsAsCurrentCandidate` | ✅ |
| AC-02 | Non-qualifying market → absent or NON_POSITIVE | `nonPassiveScanOpportunityIsExcludedFromPassiveFeed` (Scenario B) | ✅ |
| AC-03 | Stale evidence → current=false, diagnostic code present | `opportunityPastValidityWindowIsNotCurrentWithCurrentOnlyFilter` (Scenario C) | ✅ |
| AC-04 | Superseded thesis → candidate no longer current | `supersededThesisMarksExistingCandidateAsNonPositive` (Scenario D) | ✅ |
| AC-05 | Idempotent append → exactly one candidate from repeated saves | `repeatedAppendOfSameOpportunityProducesExactlyOneCurrentCandidate` (Scenario H) | ✅ |
| AC-06 | Crash recovery → expired lease on DISPATCH_REQUESTED reset to REGISTERED | `expiredLeaseOnDispatchRequestedMarketIsRecoveredToRegistered` (Scenario G) | ✅ |
| AC-07 | Gateway `/api/v1/passive-awareness` routes to market-intelligence with JWT | `passiveAwarenessGetRoutesToMarketIntelligenceWithJwtForwarding` | ✅ |
| AC-08 | Gateway preserves query parameters | `passiveAwarenessQueryParamsPreservedByGateway` | ✅ |

---

## 5. Scenarios Covered vs Not Covered

| Scenario | Description | Coverage |
|---|---|---|
| A — Qualifying market | Pipeline end-to-end | ✅ PassiveScanV1ProjectionIntegrationTest |
| B — Quiet market | No qualifying evidence | ✅ PassiveScanV1ProjectionIntegrationTest |
| C — Stale evidence | Freshness policy violation | ✅ PassiveScanV1ProjectionIntegrationTest |
| D — Superseded candidate | Thesis supersession | ✅ PassiveScanV1ProjectionIntegrationTest |
| E — Ineligible account | ActiveScan eligibility rejection | ⚠️ Covered by existing `PassiveCandidateActiveScanCreationTest` (pre-existing) |
| F — Market unavailable | No false positive on missing market data | ⚠️ Partially covered by existing `PassiveAwarenessProjectionServiceTest` (pre-existing) |
| G — Crash and recovery | Expired lease → REGISTERED | ✅ PassiveScanV1ProjectionIntegrationTest |
| H — Duplicate requests | Idempotent append | ✅ PassiveScanV1ProjectionIntegrationTest |
| I — Negative active result | No passive promotion on no-opportunity | ⚠️ Covered by existing `PassiveCandidateActiveScanCreationTest` (pre-existing) |
| F-01 — Gateway routing | `/api/v1/passive-awareness` route | ✅ GatewayDownstreamRoutingIntegrationTest |

Scenarios E, F (partial), and I are addressed by pre-existing tests. No new code needed.

---

## 6. Technical Notes

### Domain invariant on PassiveScanMarket
`analysisExecutionId` is required (non-null) for all non-SKIPPED statuses. Tests that save a
`PassiveScanMarket` in any dispatchable state must first persist a valid `AnalysisExecution`
row via `JpaAnalysisExecutionRepository` to satisfy the FK `fk_passive_scan_market_execution`.
Helper `minimalExecution()` added to test class, following the pattern in `PassiveScanPersistenceTest`.

### Hibernate first-level cache
`@Modifying` JPQL queries (e.g. `recoverExpiredMarket`) do not automatically evict the
Hibernate session cache. The crash-recovery test calls `em.flush(); em.clear()` before reloading
to ensure the reload sees the committed update rather than the stale cached entity.

### Scheduler safety
No `@Scheduled` bean is activated in the test context. The `PassiveScanDispatchCoordinator` is
not wired; dispatch logic is exercised exclusively through direct repository calls.

---

## 7. Residual Risks

| Ref | Risk | Impact |
|---|---|---|
| R-0245-1 | 19/27 diagnostic codes lack explicit test assertions (from Story 0242 review RISK-01) | Low — logic is correct; deferred to follow-on coverage work |
| R-0245-2 | No cross-service HTTP test between market-intelligence and Gateway running simultaneously | Medium — Gateway test stubs market-intelligence; simultaneous startup not tested |
| R-0245-3 | No browser/UI E2E test for the Angular awareness feed | Low — out of V1 scope; post-V1 work |

---

## 8. Outstanding Gates

| Gate | Status |
|---|---|
| Test suite passing | ✅ (6 MI tests + 17 Gateway tests) |
| No production code modified | ✅ |
| Scheduler not activated | ✅ |
| No broker contact | ✅ |
| Independent code review | ✗ — pending |
| Human approval | ✗ — pending |
