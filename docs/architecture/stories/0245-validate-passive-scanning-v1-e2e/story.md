# Story 0245 — Validate Passive Market Scanning V1 End-to-End

## Metadata

**ID:** `0245`

**Title:** Validate Passive Market Scanning V1 End-to-End

**Status:** Draft — pending human approval

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-021`, `ADR-033`, `ADR-034`, `ADR-056`, `ADR-057`, `ADR-076`, `ADR-087`, `ADR-100`, `ADR-102`

**Predecessors:** `0239`, `0240`, `0241`, `0242`, `0243`, `0244`

**Reference:** `docs/architecture/epics/0238-passive-market-scanning/integration-audit.md`

---

## Goal

Produce reproducible deterministic validation evidence that the complete
Passive Scanner V1 pipeline — from configured universe through passive scan
run, dispatch, analysis, observation, awareness projection, Angular dashboard,
passive-to-active investigation, and active scan result — is integrated,
correct, safe, and observable.

---

## Context

Epic 0238 is implementation-complete (Stories 0239–0244 merged). Integration
audit `integration-audit.md` confirms no P0 safety blocker but identifies four
P1 gaps that prevent declaring the epic `V1_INTEGRATION_VALIDATED`:

- **F-01:** No gateway routing integration test for `/api/v1/passive-awareness`.
- **F-03:** No cross-service E2E test for the complete passive pipeline.
- **F-04 / R-0242-2:** No deterministic fixture test proving a qualifying event
  creates exactly one candidate, a non-qualifying event creates none, and
  repeated identical events do not flood the awareness feed.
- **G-04-CRIT:** Story 0242 missing Implementation Report, independent review,
  and engineering report.

This Story closes F-01, F-03, and F-04. The governance gap G-04-CRIT is a
separate owner decision that this Story cannot mechanically close, but its
fixture tests will provide the validation evidence that an independent reviewer
can use to retroactively assess Story 0242.

---

## Problem

The six Epic 0238 stories were validated in isolation (per-module unit tests,
per-module Spring Boot tests). No test validates the complete runtime chain:

1. `PassiveMarketUniverse` resolves configured markets.
2. `PassiveScanDispatchCoordinator.dispatchOnce()` selects due markets.
3. `AnalysisExecution(PASSIVE)` is dispatched and completes.
4. Observation, MarketThesis, StrategyMatch, and `TradingOpportunity(PASSIVE_SCAN)` are persisted.
5. `PassiveAwarenessProjectionService.search()` returns a valid current candidate.
6. The candidate is current, has zero diagnostics, and carries correct identity and provenance.
7. The Angular feed receives the response and displays the candidate.
8. The trader selects the candidate and an authorized account; the existing ActiveScan is created.
9. Passive provenance is preserved in the scan record.
10. The scan completes truthfully (opportunity found or no-work).
11. Non-qualifying markets produce no candidate and no automatic scan.
12. Stale, superseded, and repeated-identical events behave deterministically.
13. Lease expiry and claim recovery produce safe reconciliation.
14. Idempotency and authorization regressions are confirmed at the integration boundary.

---

## Scope

### Backend: Market Intelligence in-process integration tests (new test class / suite)

Using the existing `Testcontainers` PostgreSQL pattern and the `SpringApplicationBuilder`
pattern from `HistoricalReconstructionLifecyclePostgresIntegrationTest`:

- **Scenario A — Qualifying market end-to-end (fixture-based):**
  - Configure a deterministic `PassiveMarketUniverse` with one market entry.
  - Use a mocked `MarketDataClient` returning deterministic market facts.
  - Stub `LocalAnalysisExecutionDispatcher` to deliver a qualifying analysis result
    (Observation + CANDIDATE MarketThesis + StrategyMatch + PASSIVE_SCAN opportunity).
  - Call `PassiveScanDispatchCoordinator.dispatchOnce()` with scheduler disabled.
  - Assert: `PassiveScanRun` reaches a terminal state; one `PassiveScanMarket` COMPLETED.
  - Assert: `PassiveAwarenessProjectionService.search(currentOnly=true)` returns one item.
  - Assert: item is `current=true`, `presentationState=PASSIVE_CANDIDATE`, diagnostics empty.
  - Assert: identity (`instrument`, `provider`, `productType`, `marketId`) is correct.
  - Assert: `opportunityId` and `opportunityVersion` match the persisted opportunity.

- **Scenario B — Quiet market (no qualifying evidence):**
  - Stub analysis dispatcher to deliver an observation with a NON_CANDIDATE MarketThesis.
  - Assert: projection returns `total=0` with `currentOnly=true`.
  - Assert: `total >= 1` with `currentOnly=false` (non-positive item present if applicable).
  - Assert: no automatic ActiveScan is created.

- **Scenario C/D — Stale and superseded candidates:**
  - From Scenario A outcome, advance controlled clock beyond validity window.
  - Assert: `current=false`; diagnostics contain expected freshness or validity code.
  - Advance controlled clock further; supersede thesis.
  - Assert: `current=false`; diagnostics contain `THESIS_SUPERSEDED`.

- **Scenario E — Ineligible account:**
  - From Scenario A outcome, attempt `ActiveScanApplicationService.create()` with
    an account that fails `requireOwnedAccount`.
  - Assert: scan creation is rejected; no TradePlan or risk evaluation is triggered.

- **Scenario F — Market unavailable:**
  - Stub `MarketDataClient` to return empty market facts for the configured market.
  - Assert: dispatch produces `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` or dispatcher skips the market.
  - Assert: no false-positive opportunity.

- **Scenario G — Crash and recovery (lease expiry):**
  - Create a `PassiveScanMarket` claim with a short lease, then advance clock past expiry.
  - Call `PassiveScanDispatchCoordinator.reconcileExpiredLeases()` (or equivalent).
  - Assert: claim is recovered; no duplicate logical execution is registered.
  - Assert: attempt count incremented; market not retried beyond `maxAttempts`.

- **Scenario H — Duplicate requests (idempotency):**
  - Submit the same `CreateActiveScanCommand` with the same `Idempotency-Key` twice.
  - Assert: same scan returned; no duplicate persisted.
  - Submit with same `Idempotency-Key` but different `sourcePassiveOpportunityVersion`.
  - Assert: rejected with PASSIVE_CANDIDATE error (not silently reused).

- **Scenario I — Negative active result:**
  - From Scenario A outcome, create an ActiveScan with passive provenance.
  - Stub analysis dispatcher to deliver a `COMPLETED_NO_WORK` active result.
  - Assert: scan reaches `COMPLETED_NO_WORK`; passive candidate provenance is retained in scan record.
  - Assert: no promotion of passive candidate into an executable trade.

- **Deduplication fixture test (F-04 / R-0242-2):**
  - Dispatch the same passive market configuration three times in sequence.
  - Assert: awareness projection returns exactly one current candidate, not three.

### Backend: Gateway routing integration test (extend existing test)

Extend `GatewayDownstreamRoutingIntegrationTest` to cover the
`/api/v1/passive-awareness` route:

- Add a stub HTTP server for `market-intelligence` passive-awareness endpoint.
- Assert: `GET /api/v1/passive-awareness` through the Gateway reaches the stub.
- Assert: JWT from Gateway is forwarded (Authorization header present on stub).
- Assert: `GET /api/v1/passive-awareness?currentOnly=true&size=5` preserves query parameters.

### Documentation

- Update `story.md` for Stories 0244 to reflect merged status.
- Create an engineering report for Story 0244.
- Produce a validation summary for Story 0242 based on the new fixture evidence,
  for human reviewer consumption (not a retroactive approval, but a basis for review).

---

## Out of Scope

- Enabling the passive scheduler in any test.
- Connecting to cTrader, Kraken, or any real broker.
- Creating trades, TradePlans, or risk evaluations in tests.
- Database migrations.
- AI engine invocation.
- Historical backfill.
- Browser-level E2E (UI automation).
- Multi-instance concurrency (R-0241-1); single-process only.
- Marketing, demo environments, or production deployment.
- Governance decisions for Stories 0240–0242 (those require separate human approval).

---

## Acceptance Criteria

**Backend — Scenario A (qualifying market, full pipeline):**

- [ ] A deterministic Testcontainers PostgreSQL test creates a PassiveScanRun, dispatches
      one market, and produces a PASSIVE_CANDIDATE opportunity with correct identity,
      provenance, and zero diagnostics in the awareness projection.
- [ ] The projection response serializes correctly and includes `opportunityId`,
      `opportunityVersion`, `instrument`, `provider`, `productType`, `marketId`,
      `current=true`, `presentationState=PASSIVE_CANDIDATE`, `sourceScanId`,
      `strategyMatchId`, `thesisId`.

**Backend — Scenario B (quiet market):**

- [ ] A non-qualifying analysis result produces no current passive candidate in the
      awareness projection (`currentOnly=true` returns `total=0`).
- [ ] No automatic ActiveScan is created.

**Backend — Deduplication (R-0242-2):**

- [ ] Running the same qualifying fixture three times produces exactly one current
      candidate in the awareness projection, not three.

**Backend — Stale/superseded (Scenarios C/D):**

- [ ] A candidate past its validity window is `current=false` with an appropriate
      diagnostic code.
- [ ] A superseded thesis produces `THESIS_SUPERSEDED` in diagnostics and `current=false`.

**Backend — Authorization (Scenario E):**

- [ ] An ActiveScan creation with an unauthorized account is rejected; no TradePlan,
      risk evaluation, or broker operation occurs.

**Backend — Idempotency (Scenario H):**

- [ ] Same idempotency key returns the same scan record on duplicate submission.
- [ ] A different passive candidate version on the same key is not silently reused.

**Backend — Negative result (Scenario I):**

- [ ] A `COMPLETED_NO_WORK` active result preserves passive candidate provenance in
      the scan record.

**Backend — Lease recovery (Scenario G):**

- [ ] An expired claim is recovered; the attempt count is incremented; no duplicate
      logical execution is registered.

**Gateway routing:**

- [ ] `GatewayDownstreamRoutingIntegrationTest` covers `GET /api/v1/passive-awareness`
      with JWT forwarding and query parameter preservation.

**Safety regression:**

- [ ] No test in this Story enables the passive scheduler.
- [ ] No test contacts cTrader, Kraken, or any real broker.
- [ ] No test creates a TradePlan or risk evaluation.
- [ ] ArchUnit `PassiveToActiveArchitectureTest` and `TradePlanArchitectureTest` remain green.
- [ ] All existing 1082+ market-intelligence tests remain green.
- [ ] Angular 635+ tests remain green.

**Documentation:**

- [ ] Story 0244 `story.md` status updated to "Merged — commit c034970f, PR #296".
- [ ] Story 0244 engineering report created.
- [ ] Story 0242 validation summary created for independent reviewer use.

---

## Constraints

1. The passive scheduler must remain disabled unless explicitly invoked programmatically
   with a controlled clock in an isolated test context.
2. All PostgreSQL-backed tests use Testcontainers. No assumption about a running local
   database is permitted.
3. Market Data responses are always mocked. No live provider data is used.
4. Broker adapters are never instantiated. `broker-service` is not involved.
5. AI engine is not invoked.
6. All temporal assertions use a controlled `Clock`. No `Instant.now()` in test assertions.
7. This Story does not approve Story 0242's governance gate. That gate requires a
   separate independent review and human approval.
8. This Story does not change `market-intelligence.passive-dispatch.enabled` in any
   non-test application configuration.

---

## Migration and Safety

No schema migration, configuration activation, broker operation, or deployment is
required. All tests are self-contained.

---

## Dependencies

Depends on Stories 0239–0244 (all merged). No downstream Story is blocked.

---

## Validation

The Story is complete when:

- All acceptance criteria are met.
- `mvn verify` passes for `market-intelligence` and `gateway` modules.
- `npm run check` passes for `trading-os-web`.
- A human reviews and approves the validation evidence.
