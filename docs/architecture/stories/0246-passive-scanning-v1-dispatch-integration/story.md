# Story 0246 — Passive Scanning V1 Dispatch Integration Test

## Metadata

**ID:** `0246`

**Title:** Passive Scanning V1 Dispatch Integration Test

**Status:** Implementation complete — independent review complete — conditional closure assessment pending human approval

**Priority:** P1 — Epic Closure Blocker

**Epic:** `0238`

**Related ADRs:** `ADR-087`, `ADR-102`

**Predecessors:** `0245` (corrective — addresses gaps identified in Story 0245 code review)

**Trigger document:** `docs/architecture/stories/0245-validate-passive-scanning-v1-e2e/code-review.md` (verdict: REQUIRES_CHANGES)

---

## Goal

Add a Testcontainers PostgreSQL integration test that calls
`PassiveScanDispatchCoordinator.dispatchOnce()` with a controlled market universe and verifies
that the full passive pipeline — from coordinator dispatch through to the passive awareness
projection feed — produces the correct output.

---

## Problem

Story 0245 added projection-level integration tests that seed repositories directly, bypassing the
`PassiveScanDispatchCoordinator`. As a result:

1. `PassiveScanDispatchCoordinator.dispatchOnce()` has never been called from any integration test.
   The coordinator is the central integration boundary of Epic 0238; a regression in its claim,
   dispatch, or lease-recovery path would not be caught by any existing test.

2. All Story 0245 market-intelligence tests use H2 in `MODE=PostgreSQL`. Story 0245 AC constraint
   #2 requires Testcontainers PostgreSQL. The constraint exists because H2 does not reproduce
   PostgreSQL row-locking semantics, `FOR UPDATE SKIP LOCKED`, partial index evaluation, or
   `BIGSERIAL` behavior.

3. The qualifying vs non-qualifying market determination via `PassiveMarketUniverseResolver` has
   not been verified at the integration level.

These gaps block Epic 0238 closure criterion C-07 and C-14 as recorded in
`docs/architecture/epics/0238-passive-market-scanning/validation-gap-report.md`.

---

## Context

The `PassiveScanDispatchCoordinator.dispatchOnce()` method is guarded by:

```java
if (!properties.isEnabled()) {
    metrics.skipped();
    return DispatchResult.disabledResult();
}
```

The default is `market-intelligence.passive-dispatch.enabled=false`. A test that calls
`dispatchOnce()` must override this property to `true` and must control the market universe to
avoid any live provider dependency.

The existing PostgreSQL Testcontainers pattern is in:
`market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/HistoricalReconstructionLifecyclePostgresIntegrationTest.java`

That test uses `SpringApplicationBuilder` with an overridden data-source URL pointing to the
Testcontainers-managed PostgreSQL container.

The `MutableClock` inner class (defined in `PassiveScanV1ProjectionIntegrationTest`) must be
reused or moved to a shared test utility to control freshness-sensitive assertions.

---

## Scope

### In scope

- A new integration test class `PassiveScanDispatchIntegrationTest` using Testcontainers
  PostgreSQL and a real Flyway-migrated schema.
- A test that calls `dispatchOnce()` with:
  - A controlled `PassiveMarketUniverseResolver` returning exactly one qualifying market
  - `market-intelligence.passive-dispatch.enabled=true` property override
  - `MarketDataClient` mocked to return deterministic market facts
  - A controlled `Clock` for timestamp-sensitive assertions
- Scenario A coverage (qualifying market → coordinator creates `PassiveScanRun` and
  `PassiveScanMarket`, registers `AnalysisExecution`, persists a PASSIVE_SCAN opportunity,
  and `PassiveAwarenessProjectionService.query()` returns `current=true`).
- Scenario B coverage (quiet market → coordinator runs, produces no opportunity,
  `PassiveAwarenessProjectionService.query()` returns empty or no current candidates).
- Scheduler not activated in any test (the coordinator is called directly on a test-managed
  thread; no `@Scheduled` bean is wired).
- No broker contact; no live provider calls; no production database.

### Out of scope

- Modifying or removing the existing `PassiveScanV1ProjectionIntegrationTest` tests (they remain
  valid projection-level tests).
- Multi-instance concurrency tests (R-0241-1 — deferred to V2).
- Full browser/UI E2E tests (post-V1 scope).
- Risk evaluation, TradePlan creation, or execution (not part of the passive pipeline).
- cTrader or Kraken connection of any kind.
- Enabling the passive scheduler in any environment.

---

## Acceptance Criteria

- [ ] **AC-01:** A new test class `PassiveScanDispatchIntegrationTest` exists in
  `market-intelligence/src/test`, uses `PostgreSQLContainer<>` from Testcontainers, and starts
  the Spring application context against the Testcontainers-managed database with full Flyway
  migration.

- [ ] **AC-02 (Scenario A — Qualifying market):**
  Given the coordinator is configured with one market that passes `PassiveMarketUniverseResolver`,
  when `dispatchOnce()` is called, then:
  - A `PassiveScanRun` record exists in the database.
  - A `PassiveScanMarket` record exists with `marketId` matching the configured market.
  - An `AnalysisExecution` row was registered (confirmed via `JpaAnalysisExecutionRepository`).
  - After the analysis execution is completed with a PASSIVE_SCAN `TradingOpportunity`, calling
    `PassiveAwarenessProjectionService.query()` returns a response with `current=true` and
    `origin=PASSIVE_SCAN` for that market.

- [ ] **AC-03 (Scenario B — Quiet market):**
  Given the coordinator is configured with one market, and the analysis execution produces no
  qualifying opportunity, when `PassiveAwarenessProjectionService.query()` is called, then no
  candidate with `current=true` is returned for that market.

- [ ] **AC-04:** The `PassiveScanDispatchCoordinator` is instantiated with
  `passive-dispatch.enabled=true` via property override. The passive scheduler bean is **not**
  present in the test application context (scheduler `@ConditionalOnProperty` with
  `havingValue="true"` is not satisfied via the conditional mechanism, or the scheduler is
  explicitly excluded from the test context).

- [ ] **AC-05:** `MarketDataClient` is mocked (`@MockitoBean`) in the test context. No real
  market data provider is contacted.

- [ ] **AC-06:** A controlled `Clock` is injected into the Spring context to prevent
  freshness-sensitive flakiness (following the `MutableClock` pattern already established in
  `PassiveScanV1ProjectionIntegrationTest`).

- [ ] **AC-07:** All existing tests in `PassiveScanV1ProjectionIntegrationTest` and
  `GatewayDownstreamRoutingIntegrationTest` continue to pass.

- [ ] **AC-08:** `market-intelligence` Maven build passes with the new tests included.

---

## Constraints

1. **No Testcontainers assumption about a running local database.** PostgreSQL must be provisioned
   by the test via `PostgreSQLContainer<>`. The test must pass on a clean CI environment with no
   pre-existing database.

2. **Scheduler must not be activated.** The `PassiveScanDispatchScheduler` bean must not be
   present in the test application context. The coordinator is called directly in test code.

3. **No live provider calls.** `MarketDataClient` must be mocked. `BrokerServiceClient` and any
   execution-path clients must not be reachable.

4. **Clock must be injectable.** The `PassiveAwarenessProjectionService` and
   `PassiveCandidateEvaluator` must receive a controlled `Clock` instance; `Instant.now()` must
   not be called in test-sensitive paths without going through the injected clock.

5. **No production code modifications are permitted** except to make `Clock` injectable where it
   is not already (if that change is minimal and confined to constructor injection). Any such
   change must be described in the Implementation Report.

6. **No commit or push without human approval.** This story follows the standard workflow:
   implementation → implementation report → code review → human approval → human commit.

---

## Validation

- `mvn test` on `market-intelligence` module passes with the new test class included.
- `mvn test` on `gateway` module confirms no regression in `GatewayDownstreamRoutingIntegrationTest`.
- Grep confirms `PassiveScanDispatchCoordinator` and `dispatchOnce` appear in the new test file.
- Testcontainers PostgreSQL container starts and Flyway migration runs successfully.
- Implementation Report confirms: coordinator called, `PassiveScanRun` persisted, `AnalysisExecution` registered, projection query returns `current=true` for qualifying market.

---

## Epic Closure Impact

Approval and merge of this story closes:

| Criterion | Closes |
|---|---|
| C-06 (F-04 partial) | ✓ Qualifying/non-qualifying through coordinator |
| C-07 (F-03) | ✓ Cross-service E2E validation with coordinator exercised |
| C-08 | ✓ Authorization regression at coordinator level |
| C-09 | ✓ Idempotency regression at coordinator level |
| C-10 | ✓ Restart/recovery at integration level (Testcontainers PostgreSQL) |
| C-13 | ✓ Diagnostic codes surfaced through real projection query |
| C-14 | Requires human approval of this story's evidence |

After this story, Epic 0238 may be declared `V1_INTEGRATION_VALIDATED` pending C-14 (human
approval of the validation evidence).
