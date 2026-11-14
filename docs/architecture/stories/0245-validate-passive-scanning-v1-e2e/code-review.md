# Story 0245 — Independent Code Review

**Date:** 2026-11-14
**Reviewer:** Independent (automated review — Claude Sonnet 4.6)
**Scope:** Story 0245 deliverables as merged in PR #297 (`6d98226`)
**Files reviewed:**
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/passive/PassiveScanV1ProjectionIntegrationTest.java`
- `gateway/src/test/java/com/hope/trading/gateway/GatewayDownstreamRoutingIntegrationTest.java` (additions only)

---

## Verdict

**`REQUIRES_CHANGES`**

Two P1 findings (structural violations of Story 0245's own acceptance criteria) require a corrective follow-on story before this work can be declared V1-validated. The tests as written pass and are not incorrect, but they do not satisfy the specific guarantees the story was designed to provide.

---

## P1 Findings

### P1-01 — H2 in-memory database used; Testcontainers PostgreSQL required by AC constraint #2

**Location:** `market-intelligence/src/test/resources/application-test.properties`

**Evidence:**

```
spring.datasource.url=jdbc:h2:mem:market_intelligence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
```

**Story 0245 AC constraint #2 (verbatim):**

> All PostgreSQL-backed tests use Testcontainers. No assumption about a running local database is permitted.

The constraint exists because H2 in `MODE=PostgreSQL` does not reproduce PostgreSQL-specific behaviors:
row-level locking semantics, `FOR UPDATE SKIP LOCKED`, partial index evaluation, `BIGSERIAL` sequence behavior, and Flyway migration compatibility on the real JDBC driver. Market Intelligence already has a Testcontainers PostgreSQL pattern available in `HistoricalReconstructionLifecyclePostgresIntegrationTest` using `SpringApplicationBuilder` + `PostgreSQLContainer<>`. The 0245 implementation did not adopt this pattern.

**Impact:** The projection serialization, Flyway migration, and JPQL queries pass on H2 but have not been verified against the target runtime database engine. The concurrency behavior of `@Modifying` JPQL (`recoverExpiredMarket`) in particular requires PostgreSQL row-locking to be meaningful.

**Required fix:** Migrate `PassiveScanV1ProjectionIntegrationTest` to Testcontainers PostgreSQL following the `HistoricalReconstructionLifecyclePostgresIntegrationTest` pattern. This is the primary target of corrective Story 0246.

---

### P1-02 — PassiveScanDispatchCoordinator.dispatchOnce() is never called in any test

**Location:** `market-intelligence/src/test/java/…/PassiveScanV1ProjectionIntegrationTest.java`

**Evidence:**

```bash
$ grep -r "PassiveScanDispatchCoordinator\|dispatchOnce" market-intelligence/src/test
# No output — zero matches
```

**Story 0245 AC for Scenario A (verbatim from story.md):**

> AC-01: A qualifying market goes through the full passive pipeline and surfaces as a current PASSIVE_CANDIDATE.

The word "pipeline" is unambiguous in the story's scenario context (Section 7, Scenario A):

> Scenario A: qualifying market
> Given: Coordinator is configured with one market that passes `PassiveMarketUniverseResolver`.
> When: `dispatchOnce()` runs.
> Then: A `PassiveScanRun` and `PassiveScanMarket` are persisted, an `AnalysisExecution` is registered, the resulting opportunity is PASSIVE_SCAN origin, and `PassiveAwarenessProjectionService.query()` returns `current=true` for that market.

The 0245 implementation bypasses this entirely. The test `qualifyingPassiveScanOpportunityAppearsAsCurrentCandidate` seeds the database directly via `opportunityRepo.append(...)`, `thesisRepo.save(...)`, and `strategyMatchRepo.save(...)` — the `PassiveScanDispatchCoordinator` is never constructed or called.

**What is confirmed by the existing test:** The `PassiveAwarenessProjectionService` correctly evaluates pre-seeded data. This is a valid and useful test, but it verifies a different invariant than the one the story specified.

**What remains unverified:**
- `PassiveMarketUniverseResolver.resolve()` integration with real configuration
- `PassiveScanRun` and `PassiveScanMarket` creation by the coordinator
- FK constraint `fk_passive_scan_market_execution` satisfied by the coordinator's registered `AnalysisExecution`
- The coordinator's `markDispatched` / `updateAttemptStatus` lifecycle under real Flyway-migrated schema
- Lease acquisition and claim persistence through the full coordinator path
- The end-to-end chain: universe resolution → coordinator → scan run → analysis execution → opportunity persistence → projection query

**Root cause:** `PassiveDispatchProperties.enabled` defaults to `false` in `application.properties`. A `dispatchOnce()` call with the default configuration returns `DispatchResult.disabledResult()` immediately. The test would need to either: (a) override the property to `true` and inject a controlled `PassiveMarketUniverseResolver`, or (b) call `dispatchOnce()` on a coordinator instance constructed with `enabled=true`. Neither approach was taken.

**Impact:** The primary end-to-end validation claim of Story 0245 is not satisfied. The coordinator, which is the central integration boundary between market universe configuration and the passive pipeline, has no test coverage at the integration level.

**Required fix:** A corrective story (0246) must add an integration test that constructs a `PassiveScanDispatchCoordinator` with a controlled universe, calls `dispatchOnce()`, and asserts the resulting state via `PassiveAwarenessProjectionService.query()`. This requires Testcontainers PostgreSQL (see P1-01).

---

## P2 Findings

### P2-01 — Implementation report AC table has misleading ✅ marks

**Location:** `implementation-report.md` Section 4

The report maps AC-01 ("Qualifying market → PASSIVE_CANDIDATE, current=true") to `qualifyingPassiveScanOpportunityAppearsAsCurrentCandidate` and marks it ✅. This is factually incorrect relative to the story's AC specification: the AC requires the qualifier to be verified *through the full pipeline*, not through direct repository seeding. The implementation report should have marked AC-01 as `⚠️ PARTIALLY_COVERED` with a note explaining the coordinator was not exercised.

**Impact:** The misleading ✅ could lead a human reviewer to incorrectly approve the story as fully satisfying AC-01. No code change required — this will be corrected in the Story 0245 engineering report and the corrective story.

### P2-02 — Story status stale

**Location:** `docs/architecture/stories/0245-validate-passive-scanning-v1-e2e/story.md`

Status reads: `Draft — pending human approval`. PR #297 was merged on 2026-11-14 at `6d98226`. The status should read "Merged — PR #297 (`6d98226`) — 2026-11-14 — independent review complete — corrective Story 0246 required."

---

## What Is Confirmed as Correct

The following aspects of the Story 0245 implementation are correct and do not require rework:

| Area | Verdict | Notes |
|---|---|---|
| Gateway F-01 coverage | ✅ FULLY_VERIFIED | Two new tests: JWT forwarding + query param preservation both pass |
| Projection evaluation correctness (Scenario C/D) | ✅ CORRECT | `opportunityPastValidityWindowIsNotCurrentWithCurrentOnlyFilter`, `supersededThesisMarksExistingCandidateAsNonPositive` test the evaluator directly |
| Deduplication at projection level (Scenario H) | ✅ CORRECT | `repeatedAppendOfSameOpportunityProducesExactlyOneCurrentCandidate` confirms idempotent append |
| Lease recovery via `recoverExpiredMarket` (Scenario G) | ✅ CORRECT (H2) | Mechanism is correct; must be re-verified on PostgreSQL per P1-01 |
| Non-PASSIVE_SCAN origin excluded from feed (Scenario B) | ✅ CORRECT | Tests origin-based exclusion at projection level |
| Flyway migration compatibility on H2 | ✅ PASSES | Does not validate PostgreSQL behavior |
| `em.flush(); em.clear()` before reload in Scenario G | ✅ CORRECT | Addresses Hibernate L1 cache stale-read issue |
| `@MockitoBean MarketDataClient` | ✅ CORRECT | No live provider calls in any test |
| Scheduler not activated | ✅ CONFIRMED | `@ConditionalOnProperty` and disabled default guarantee no scheduler bean |
| No production code modified | ✅ CONFIRMED | Only test files added/modified |

---

## Coverage Classification (Revised)

| AC / Scenario | Story 0245 Claim | Actual Coverage | Revised Classification |
|---|---|---|---|
| AC-01 / Scenario A — Full pipeline qualifying market | ✅ in report | Coordinator bypassed; direct repo seed | **NOT_VERIFIED at pipeline level** |
| AC-02 / Scenario B — Non-qualifying excluded | ✅ in report | Projection-level origin exclusion | **PARTIALLY_VERIFIED** |
| AC-03 / Scenario C — Stale evidence | ✅ in report | Projection evaluator direct call | **PARTIALLY_VERIFIED** |
| AC-04 / Scenario D — Superseded thesis | ✅ in report | Projection evaluator direct call | **PARTIALLY_VERIFIED** |
| AC-05 / Scenario H — Idempotent append | ✅ in report | Projection deduplication direct seed | **PARTIALLY_VERIFIED** |
| AC-06 / Scenario G — Lease recovery | ✅ in report | `recoverExpiredMarket` direct call (H2) | **PARTIALLY_VERIFIED** |
| AC-07 / F-01 — Gateway route + JWT | ✅ in report | Gateway integration test | **FULLY_VERIFIED** |
| AC-08 / F-01 — Query params preserved | ✅ in report | Gateway integration test | **FULLY_VERIFIED** |
| Scenario E — Ineligible account | ⚠️ pre-existing | `PassiveCandidateActiveScanCreationTest` unit | **UNIT_ONLY** |
| Scenario I — Negative active result | ⚠️ pre-existing | `PassiveCandidateActiveScanCreationTest` unit | **UNIT_ONLY** |

---

## Required Follow-On Work

**Story 0246** (corrective) must address:

1. Convert `PassiveScanV1ProjectionIntegrationTest` to Testcontainers PostgreSQL (or add a separate PostgreSQL-backed test class following the `HistoricalReconstructionLifecyclePostgresIntegrationTest` pattern).
2. Add an integration test that calls `PassiveScanDispatchCoordinator.dispatchOnce()` with:
   - A controlled `PassiveMarketUniverseResolver` returning one qualifying market
   - `enabled=true` property override
   - Testcontainers PostgreSQL
   - Then asserts: `PassiveScanRun` created, `PassiveScanMarket` in `DISPATCH_REQUESTED`, `AnalysisExecution` registered, and `PassiveAwarenessProjectionService.query()` returns `current=true` for that market
3. Add a "quiet market" coordinator test: universe returns a market that produces no qualifying opportunity → feed returns empty or NON_POSITIVE
4. Update Story 0245 `story.md` status to reflect merge.

---

## Summary

Story 0245 adds useful projection-level tests and resolves the P1 gateway routing gap (F-01). The two P1 findings above prevent it from satisfying its own stated acceptance criteria. The gap is precisely bounded: the `PassiveScanDispatchCoordinator` integration path and PostgreSQL compatibility are what remain unverified. These are the focus of corrective Story 0246.
