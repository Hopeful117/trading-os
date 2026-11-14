# Epic 0238 — V1 Integration Audit

**Date:** 2026-11-14
**Status:** Audit complete — pending human review and approval
**Scope:** Stories 0239–0244, cross-service boundaries, E2E validation readiness

---

## A. Repository Baseline

| Item | Value |
|---|---|
| Branch | `main` |
| HEAD | `c034970fd79046915a37505d3827fb06ead8be15` |
| Previous remote HEAD | `c034970fd79046915a37505d3827fb06ead8be15` ✓ unchanged |
| Working tree | Clean. One untracked file: `CLAUDE.md` (preserved, excluded) |
| Latest migration | `V31__allow_passive_skipped_without_execution.sql` |
| Angular build | Passes. Bundle 1.03 MB (budget raised to 1.5 MB error limit in Story 0244) |
| Java test baseline | `market-intelligence` last verified at 1082 tests (Story 0243 engineering report) |

All six Story PRs merged to `main` before this audit.

---

## B. Epic 0238 Implementation Status

### Story 0239 — Define the Bounded Passive Market Universe

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ Approved 2026-11-13 | `repository-analysis.md` |
| Implementation Plan | ✓ Approved 2026-11-13 | `implementation-plan.md` |
| Implementation | ✓ | PR #291 / `76480d09` |
| Independent review | ✓ | Per `story.md` and `implementation-report.md` |
| Human approval | ✓ | Per `story.md` |
| Merged | ✓ | PR #291 |
| Implementation Report | ✓ | `implementation-report.md` |

Code verification:

- `PassiveMarketUniverseProperties` / `PassiveMarketUniverseResolver` exist in `market-intelligence/src/main`.
- `PassiveDispatchProperties.enabled` defaults `false`. ✓
- Tests: `PassiveMarketUniversePropertiesTest`, `PassiveMarketUniverseResolverTest` confirmed in implementation report.
- No scheduler, no public endpoint, no migration.

**Residual risk:** None material.

---

### Story 0240 — Persist the PassiveScanRun Lifecycle

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ | `repository-analysis.md` |
| Implementation Plan | ✓ Approved 2026-11-13 | `implementation-plan.md` |
| Implementation | ✓ | PR #292 / `c28a246d` |
| Independent review | ✗ **Missing** | `story.md` explicitly: "GitHub reports no submitted review for PR #292" |
| Human approval | ✗ **Missing** | Pending per story |
| Merged | ✓ | PR #292 (CI passed) |
| Implementation Report | ✓ | `implementation-report.md` |

Code verification:

- `PassiveScanRun`, `PassiveScanMarket`, `PassiveScanMarketAttempt` domain types exist.
- `V29__passive_scan_run.sql` Flyway migration present (v29).
- `PassiveScanPersistenceTest` uses `@SpringBootTest @ActiveProfiles("test")` — real in-process DB (H2).
- Concurrency and restart tests confirmed in implementation report.

**Residual risk (G-02-REV):** Independent code review and human validation missing. Implementation is functionally sound but lacks the governance gate. See governance section.

---

### Story 0241 — Add Bounded Recurring Passive Dispatch

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ | `repository-analysis.md` |
| Implementation Plan | ✓ Approved 2026-11-14 | `implementation-plan.md` |
| Implementation | ✓ | PR #293 and PR #294 / `0ec172d1`, `7aa7a2a8` |
| Independent review | ✗ **Missing** | `story.md`: "review pending" |
| Human approval | ✗ **Missing** | Pending per story |
| Merged | ✓ | PR #293 and #294 |
| Implementation Report | ✓ | `implementation-report.md` |

Code verification:

- `PassiveScanDispatchScheduler` annotated `@ConditionalOnProperty(prefix = "market-intelligence.passive-dispatch", name = "enabled", havingValue = "true")`. Scheduler bean only created when explicitly enabled. ✓
- Default `enabled = false` in `PassiveDispatchProperties`. ✓
- `PassiveScanDispatchCoordinator` and claim/lease recovery classes exist.
- `V30__bounded_passive_dispatch.sql` migration present.

**Known remaining technical risks from Implementation Report:**

| Ref | Risk |
|---|---|
| R-0241-1 | Multi-instance deployment: global concurrency count is durable but not an atomic distributed capacity lease. A second instance could exceed the bound on simultaneous scheduler ticks. |
| R-0241-2 | Scheduler metrics (selected / skipped / budget-exceeded) not yet implemented as dedicated low-cardinality metrics. |
| R-0241-3 | Retry delay follows market cadence; no separate persisted exponential-backoff field. |
| R-0241-4 | Provider request budget is enforced through existing `MarketEligibilityPolicy` / Market Data facts, not a new passive-specific budget ledger. |

**Residual risk (G-03-REV):** Independent code review missing. R-0241-1 is a P2 risk for multi-instance deployment, but V1 targets single-instance operation. R-0241-2 through R-0241-4 are P2 quality improvements.

---

### Story 0242 — Expose the Passive Awareness Lifecycle

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ | `repository-analysis.md` |
| Implementation Plan | ✓ | `implementation-plan.md` |
| Implementation | ✓ | Commit `41b9a741` bundled in PR #294 |
| Independent review | ✗ **Missing** | `story.md`: explicitly missing |
| Human approval | ✗ **Missing** | `story.md`: explicitly missing |
| Merged | ✓ | PR #294 (CI passed) |
| Implementation Report | ✗ **Missing** | No `implementation-report.md` file exists |
| Engineering Report | ✗ **Missing** | No `engineering-report.md` file exists |

Code verification:

- `PassiveAwarenessController` at `/api/v1/passive-awareness` exists. ✓
- `PassiveAwarenessProjectionService`, `PassiveCandidateEvaluator` exist. ✓
- 27 diagnostic codes from `PassiveCandidateEvaluator` mapped in `PassiveAwarenessResponse`. ✓
- `MiSecurityConfiguration` includes `/api/v1/passive-awareness/**` in `.authenticated()` rule. ✓
- `MiSecurityIntegrationTest.missingJwtIsRejectedOnPassiveAwarenessRead` passes (unauthenticated 401 test). ✓
- `PassiveAwarenessControllerTest` covers projection serialization (single test, shallow). ⚠
- Shared `PassiveCandidateEvaluator` reused by Story 0243 validator (closes R-0242-1 from engineering report perspective). ✓
- No `TradePlan`, `broker`, or `risk` dependencies in `PassiveCandidateEvaluator` or `PassiveAwarenessProjectionService` (enforced by `TradePlanArchitectureTest`). ✓

**Outstanding risks from Story 0243 engineering report:**

| Ref | Description |
|---|---|
| R-0242-2 | No deterministic fixture test proving: qualifying event → one candidate, non-qualifying → none, repeated identical event → no flood. (Required by AC in story.md.) |
| R-0242-3 | Coverage of boundary conditions (all 27 diagnostic codes, supersession edge cases) is not documented. |
| R-0242-4 | Deduplication of repeated passive scan runs with identical market fingerprints: no explicit fixture test. |
| R-0242-5 | Authorization: no cross-user leakage regression test; market intelligence serves user-scoped data but passive awareness is currently all-users (account-independent). |

**Residual risk (G-04-CRIT):** This is the most significant governance gap. Missing Implementation Report means acceptance criteria were never formally signed off. The projection code exists and passes CI, but the gap in documentation and independent review leaves the Story 0242 quality gate formally open.

---

### Story 0243 — Connect Passive Candidates to Active Investigation

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ Approved 2026-11-14 | `repository-analysis.md` |
| Implementation Plan | ✓ Approved 2026-11-14 | `implementation-plan.md` |
| Code Review | ✓ Approved 2026-11-14 | `code-review.md` |
| Human approval | ✓ 2026-11-14 | `engineering-report.md` section 2 |
| Merged | ✓ | PR #295 / `97d8a865` |
| Implementation Report | ✓ | `implementation-report.md` |
| Engineering Report | ✓ | `engineering-report.md` |

Code verification:

- `PassiveCandidateScanValidator.validate()` performs full server-side revalidation. ✓
- Error codes verified: 404, 409 (`SUPERSEDED`, `NOT_CURRENT`), 422 (`INVALID_ORIGIN`, `MARKET_MISMATCH`), 503 (`IDENTITY_UNAVAILABLE`). ✓
- `PassiveCandidateProvenance` stored in `active_scans.payload` JSON. ✓
- ArchUnit `PassiveToActiveArchitectureTest.passiveScanningNeverCreatesActiveScans` enforces no passive package may call `ActiveScanApplicationService`. ✓
- ArchUnit `candidateValidationHasNoTradingAuthorityDependencies` enforces no TradePlan/Risk/broker deps. ✓
- Existing callers unaffected (golden-hash idempotency test). ✓
- `PassiveCandidateActiveScanCreationTest` is a deterministic fixture-based module-level E2E test. ✓

**Residual risk (G-05-MINOR):** Candidate-version race (pre-existing, append-only architecture). Error precedence (scope mismatch checked before freshness). Both are documented and accepted.

---

### Story 0244 — Passive Awareness Dashboard

| Gate | Status | Evidence |
|---|---|---|
| Repository Analysis | ✓ | `repository-analysis.md` |
| Implementation Plan | ✓ | `implementation-plan.md` |
| Implementation | ✓ | PR #296 / `c034970f` merged to main |
| Independent review | ✗ **Missing** | Story status stale ("awaiting code review and human commit") |
| Human approval | ✗ **Missing** | Stale status |
| Merged | ✓ | PR #296 |
| Implementation Report | ✓ | `implementation-report.md` |
| Engineering Report | ✗ **Missing** | No `engineering-report.md` |

**Documentation discrepancy:** Story 0244 `story.md` status reads "Implementation complete — 2026-11-14 — awaiting code review and human commit". PR #296 was merged to `main` at `c034970f`. Status is stale. All 10 ACs are marked `[x]` in `story.md`.

Code verification:

- `PassiveAwarenessService.query()` constructs `GET /api/v1/passive-awareness` with correct params. ✓
- Gateway URL resolution: `environment.gatewayUrl` = `/api/` → full path `/api/v1/passive-awareness`. ✓ Matches gateway route.
- `PassiveCandidateInvestigatePanel.startInvestigation()` builds `CreateActiveScanRequest` with `scopeMode: 'SELECTED'`, `requestedMarketIds: [candidate.marketId]`, `sourcePassiveOpportunityId`, `sourcePassiveOpportunityVersion`. ✓ Matches `PassiveCandidateScanValidator.scopeMatches()` requirement exactly.
- Idempotency key: `crypto.randomUUID()` per explicit Investigate click, stored in `storedIdempotencyKey`, reused on retry. ✓
- `takeUntilDestroyed()` on polling stream. ✓
- Safety regression test: `createScan` not called on feed load. ✓
- 635 Angular unit tests pass (66 test files). ✓

**Residual risk (G-06-DOC):** Independent code review and engineering report missing. Bundle size at 1.03 MB (warning threshold 700 kB — already in warning range before this story).

---

## C. Architecture Integration Assessment

### Pipeline trace

```text
[Gateway]        /api/v1/passive-awareness/**      → market-intelligence (lb://market-intelligence)
                 /api/v1/intelligence/scans         → market-intelligence
                 JWT forward filter

[Market Intel]   PassiveAwarenessController         → PassiveAwarenessProjectionService
                 → PassiveCandidateEvaluator
                 → TradingOpportunityRepository (finds PASSIVE_SCAN opportunities)
                 → MarketThesisRepository
                 → StrategyMatchRepository
                 → MarketDataClient (identity resolution)

[Market Intel]   ActiveScanApplicationService.create()
                 → ActiveScanCreationTransaction
                 → PassiveCandidateScanValidator.validate()
                    → PassiveCandidateEvaluator.evaluateOne()
                    → scope check (SELECTED + candidate market only)
                 → PassiveCandidateProvenance persisted in scan payload

[Angular]        PassiveAwarenessService → GET /api/v1/passive-awareness
                 PassiveCandidateInvestigatePanel → POST /api/v1/intelligence/scans (with Idempotency-Key)
                 → timer-based polling → GET /api/v1/intelligence/scans/{id}
                 → navigate to /decision-workspace on OPPORTUNITY_FOUND
```

### Cross-service contract matrix

| Boundary | Producer | Consumer | Contract | Auth | Idempotency | Test coverage |
|---|---|---|---|---|---|---|
| `GET /api/v1/passive-awareness` | Market Intelligence | Angular | `PassiveAwarenessPageResponse` | JWT (401 without it) | N/A (read) | Unit (MI controller + Angular service) |
| `POST /api/v1/intelligence/scans` | Market Intelligence | Angular | `CreateActiveScanRequest` + `Idempotency-Key` header | JWT | Server-side fingerprint | Unit (MI creation + Angular panel) |
| `GET /api/v1/intelligence/scans/{id}` | Market Intelligence | Angular | `ActiveScanResponse` | JWT | N/A (read) | Unit |
| `GET /api/v1/passive-awareness` Gateway routing | Gateway | Market Intelligence | Path prefix `/api/v1/passive-awareness/**` | JWT forward | N/A | Gateway routing config (no routing integration test) ⚠ |
| PassiveScanRun persistence | Market Intelligence | PostgreSQL | `V29` / `V30` tables | Service-internal | Uniqueness constraints | Spring @SpringBootTest H2 + Postgres Testcontainers |
| Passive dispatch → analysis execution | PassiveScanDispatchCoordinator | AnalysisExecutionService | `register(PASSIVE)` call | Service-internal | AnalysisExecution idempotency key | Unit (dispatch coordinator) |
| Passive analysis → opportunity | LocalAnalysisExecutionDispatcher | TradingOpportunity persistence | `PASSIVE_SCAN` origin opportunity | Service-internal | AnalysisExecution idempotency | Unit (analysis pipeline) |

### Contract risk: Gateway routing integration test gap

The `GatewayDownstreamRoutingIntegrationTest` stubs market-intelligence, trading-core, and news endpoints but does **not** cover the `passive-awareness` route added in Story 0242.

If the gateway route entry is modified or removed, this change would not be caught by any existing test. This is a **P1 integration blocker** for V1 closure.

---

## D. Critical Findings

### P0 — Safety or Correctness Blockers

**None confirmed.**

Specific safety properties verified:

| Property | Verification |
|---|---|
| Passive scanning cannot create ActiveScans | ArchUnit `PassiveToActiveArchitectureTest.passiveScanningNeverCreatesActiveScans` |
| Passive scanning cannot create TradePlans | ArchUnit `TradePlanArchitectureTest.aggregateIsImmutableAndContainsNoBrokerExecutionPositionOrPnlState`, `candidateValidationHasNoTradingAuthorityDependencies` |
| Frontend cannot auto-create scans | Safety regression test in `passive-awareness-feed.spec.ts` |
| Scheduler disabled by default | `@ConditionalOnProperty(havingValue = "true")` verified in source |
| Passive endpoint requires authentication | `MiSecurityConfiguration` + `MiSecurityIntegrationTest` |
| Account authorization unchanged | `PassiveCandidateScanValidator` does not bypass `requireOwnedAccount` |
| No broker writes from passive path | No broker adapter dependency in `application.passive` package |

### P1 — V1 Integration Blockers

| ID | Finding | Location | Evidence |
|---|---|---|---|
| F-01 | **No Gateway routing integration test for `/api/v1/passive-awareness`** | `gateway/src/test/…/GatewayDownstreamRoutingIntegrationTest.java` | Route exists in `GatewayRouteConfig.java:146` but gateway integration test does not cover it. Route breakage would be silent. |
| F-02 | **Story 0242 implementation fully delivered but no Implementation Report, no independent code review, and no engineering report exist.** The formal quality gate for 0242 is open. | `docs/architecture/stories/0242-passive-awareness-lifecycle/` | Missing files confirmed by `ls`. Story status explicitly records this. |
| F-03 | **No cross-service E2E test exists for the complete V1 pipeline.** Each service is tested in isolation; no test validates the full chain from passive universe configuration through passive scan run creation, dispatch, analysis, opportunity projection, awareness feed response, and passive-to-active investigation in one deterministic flow. | Repository-wide | Confirmed by search for `*E2eTest*`, `*PassiveWorkflow*` — none found. |
| F-04 | **R-0242-2: No fixture test proving a qualifying event creates exactly one candidate, a non-qualifying market creates none, and repeated identical events do not flood the feed.** This was an explicit acceptance criterion for Story 0242. | `market-intelligence/src/test` | No `PassiveAwarenessFixtureTest` or equivalent found. |

### P2 — Quality Improvements

| ID | Finding | Location |
|---|---|---|
| Q-01 | Story 0244 `story.md` status stale: "awaiting code review and human commit" but PR #296 is merged. | `docs/architecture/stories/0244-passive-awareness-dashboard/story.md` |
| Q-02 | Stories 0240 and 0241 independent code reviews missing (CI passed; governance gate not satisfied). | `story.md` for each |
| Q-03 | Story 0241 R-0241-1: multi-instance deployment would not strictly bound passive concurrency. | `PassiveScanDispatchCoordinator` |
| Q-04 | Story 0241 R-0241-2: no dedicated low-cardinality scheduler metrics for observability. | `PassiveScanDispatchScheduler` |
| Q-05 | Angular bundle at 1.03 MB; `maximumWarning` threshold (700 kB) already exceeded. The error limit was raised to 1.5 MB. Future stories should consider lazy-loading the awareness feed. | `trading-os-web/angular.json` |
| Q-06 | `GatewayDownstreamRoutingIntegrationTest` does not stub market-data or passive-awareness routes; passive-awareness route regression would be silent without the test in F-01. | Gateway integration test |

---

## E. End-to-End Validation Strategy

### Recommended test layer combination

| Layer | Purpose | Infrastructure | V1 coverage |
|---|---|---|---|
| Unit / domain | Individual rules, state machines, mappers | In-memory mocks | Already covers most of 0239–0244 |
| Component / Spring `@SpringBootTest` | Persistence, security, serialization per service | H2 / Testcontainers PostgreSQL | Exists for MI; gap in passive-awareness projection fixture (F-04) |
| Gateway routing integration | Route contract regression | Stub HTTP servers (existing pattern) | Missing for passive-awareness (F-01) |
| Cross-service module E2E | Complete passive pipeline in one test process, broker disabled | Testcontainers PostgreSQL, real MI application, mocked Market Data + broker | **Does not exist — proposed in Story 0245** |
| Browser / UI E2E | Angular + real API end-to-end | Angular dev-server + real or mock backend | Out of scope for V1 closure |

### Scenario coverage matrix

| Scenario | Type | Existing test | Proposed Story 0245 coverage |
|---|---|---|---|
| A — Qualifying market → candidate | Cross-service E2E | `PassiveCandidateActiveScanCreationTest` (module-level, mocked repositories) | Full in-process E2E with real persistence |
| B — Quiet market / no candidate | Component fixture | Missing (F-04) | Add to passive-awareness projection fixture tests |
| C — Stale evidence | Validator unit test | `PassiveCandidateScanValidator` stale tests | Confirm diagnostic code surfaced in feed response |
| D — Superseded candidate | Validator unit test | `PassiveCandidateScanValidator` SUPERSEDED tests | Confirm 409 response from full creation path |
| E — Ineligible account | ActiveScan creation test | Existing `COMPLETED_NO_WORK` test | Confirm eligibility rejection with passive provenance |
| F — Market unavailable | Validator unit test | 503 `IDENTITY_UNAVAILABLE` test | Confirm closed-market / unavailable-provider outcome |
| G — Crash and recovery | Persistence test | `PassiveScanPersistenceTest` (partial) | Lease expiry + reconciliation test with controlled clock |
| H — Duplicate requests | Idempotency unit test | Fingerprint golden-hash test | Confirm same key returns same scan; different version → new scan |
| I — Negative active result | Reconciliation test | `COMPLETED_NO_OPPORTUNITY` reconciliation test | Confirm passive candidate not promoted |

### Recommended test infrastructure

- Reuse existing Testcontainers pattern from `HistoricalReconstructionLifecyclePostgresIntegrationTest`.
- Reuse `PassiveCandidateFixtures` and `InMemoryAnalysisExecutionRepository` patterns.
- Use `SpringApplicationBuilder` with `market-intelligence` profile `test` and passive dispatch disabled.
- Inject a controlled `Clock` for temporal consistency tests.
- Mock `MarketDataClient` to return deterministic market facts without network calls.
- Stub `LocalAnalysisExecutionDispatcher` to deliver test analysis results deterministically.
- Do NOT connect to cTrader, Kraken, or any live broker.
- Do NOT enable the passive scheduler in any E2E test.

---

## F. Safety Verdict

### Confirmed protections

| Property | Mechanism | Status |
|---|---|---|
| No automatic ActiveScan from passive scanning | ArchUnit `PassiveToActiveArchitectureTest` | ✓ Confirmed |
| No TradePlan creation from passive path | ArchUnit `TradePlanArchitectureTest` + `PassiveToActiveArchitectureTest.candidateValidationHasNoTradingAuthorityDependencies` | ✓ Confirmed |
| Scheduler disabled by default | `@ConditionalOnProperty(havingValue = "true")` + `enabled=false` default | ✓ Confirmed |
| Passive awareness endpoint requires authentication | `MiSecurityConfiguration` + `MiSecurityIntegrationTest` | ✓ Confirmed |
| Account authorization not bypassed | `PassiveCandidateScanValidator` delegates to existing `requireOwnedAccount` | ✓ Confirmed |
| No broker writes from passive path | No broker adapter dependency in passive packages | ✓ Confirmed |
| Provider requests bounded | `MarketEligibilityPolicy` check before dispatch | ✓ Confirmed (not a dedicated passive budget ledger; R-0241-4) |
| Frontend does not auto-create scans | Safety regression test in Angular feed spec | ✓ Confirmed |
| Idempotency key per click | `crypto.randomUUID()` stored in component state | ✓ Confirmed |

### Unverified assumptions / open risks

| Assumption | Risk | Severity |
|---|---|---|
| Passive awareness projection correctly deduplicates repeated PASSIVE_SCAN opportunities | No deterministic fixture test (F-04 / R-0242-2) | P1 |
| Gateway routing for `/api/v1/passive-awareness` will not regress | No routing integration test (F-01) | P1 |
| Multi-instance deployment would bound concurrency | Concurrency count is durable, not atomic across instances (R-0241-1) | P2 (single instance V1) |
| Angular bundle will not exceed 1.5 MB error limit | Bundle at 1.03 MB, warning already exceeded | P2 |

---

## G. Governance Gaps

| Story | Impl Report | Ind. Review | Human Approval | Engineering Report | Current Status |
|---|---|---|---|---|---|
| 0239 | ✓ | ✓ | ✓ | (not required per workflow) | Fully closed |
| 0240 | ✓ | ✗ Missing | ✗ Missing | (not required; no review gate) | **Open — G-02-REV** |
| 0241 | ✓ | ✗ Missing | ✗ Missing | (not required) | **Open — G-03-REV** |
| 0242 | ✗ Missing | ✗ Missing | ✗ Missing | ✗ Missing | **Open — G-04-CRIT** |
| 0243 | ✓ | ✓ | ✓ | ✓ | Fully closed |
| 0244 | ✓ | ✗ Missing | ✗ Missing | ✗ Missing | **Open — G-06-DOC (status stale)** |

**G-04-CRIT** is the most critical gap: Story 0242 is the backbone projection service. Code exists and CI passed, but no quality gate artifact exists for it. Before marking Epic 0238 `V1_INTEGRATION_VALIDATED`, either:
1. A retrospective independent review and implementation report for 0242 are produced and human-approved, **or**
2. The gap is explicitly accepted as a residual risk by the project owner, documented in writing.

**G-02-REV, G-03-REV:** Stories 0240 and 0241 share the same pattern — merged without independent review. Both are foundational persistence and scheduling layers. Same resolution path as G-04-CRIT.

**G-06-DOC:** Story 0244 `story.md` status is stale. Needs update to "Merged — commit c034970f, PR #296" and an engineering report.

---

## H. Proposed Engineering Story

Proposed as Story **0245**. Full artifact created at:

```
docs/architecture/stories/0245-validate-passive-scanning-v1-e2e/story.md
```

This story is a documentation and test-code deliverable. It does not enable the scheduler, does not connect to live brokers, and does not require database migrations.

---

## I. Temporal Consistency Assessment

### Timestamps in use across the pipeline

| Timestamp | Source | Consumer | Risk |
|---|---|---|---|
| `evaluatedAt` | Passive analysis execution completion | PassiveAwarenessProjection freshness | Requires monotonic clock; H2/Postgres are authoritative |
| `sourceCutoff` | MarketThesis evidence cutoff | Freshness diagnostic in PassiveCandidateEvaluator; `PassiveCandidateScanValidator.assess()` | Must be set by analysis; null handled gracefully |
| `validFrom` / `validUntil` | TradingOpportunity | Validity window in projection; supersession detection | Null `validUntil` handled in Angular (displayed as "—") |
| `observationTimes` | Observation created-at values | Display in Angular feed | Reference only; no deterministic freshness enforcement on these |
| `validatedAt` | PassiveCandidateProvenance | Recorded at scan creation time | Prevents retroactive version changes; `Instant.now()` within validator transaction |
| Scheduler `fixedDelay` | `PassiveDispatchProperties` | Dispatch timing | Minimum 5 minutes; not clock-aware for tests → requires mocked clock for unit tests |

**Key determinism requirement:** E2E tests must inject a controlled `Clock` to prevent timestamp-related flakiness. The existing `MutableClock` pattern in `PassiveCandidateActiveScanCreationTest` should be reused.

**Risk T-01:** `PassiveCandidateEvaluator.withinBound(sourceCutoff, now, bound)` uses `Instant.now()` from the injection context. In tests, the clock must be fixed before the evaluator runs. This is handled in module-level tests but is unverified at cross-service level.

---

## J. Observability Assessment

### Available today

| Diagnostic | Location | Coverage |
|---|---|---|
| Passive candidate diagnostics (27 codes) | `PassiveCandidateEvaluator` → `PassiveAwarenessResponse.diagnostics` | ✓ Surfaced in Angular feed |
| Passive awareness `current` / `presentationState` | `PassiveAwarenessResponse` | ✓ |
| Scan creation error codes | `PassiveCandidateScanValidator` → HTTP 4xx/5xx with `code` field | ✓ Angular maps to labels |
| Passive evidence assessment dimensions | `PassiveCandidateProvenance.evidenceAssessment` stored in scan payload | ✓ |
| Passive scan run / market attempt records | `passive_scan_runs`, `passive_scan_markets`, `passive_scan_market_attempts` | ✓ Durable for audit |
| Dispatch coordinator logs | `PassiveScanDispatchScheduler` / coordinator `info` log on dispatch | Minimal |
| Test output | 1082 market-intelligence tests | ✓ |

### Gaps

| Gap | Severity |
|---|---|
| No dedicated scheduler metrics (selected / skipped / dispatch count / retry count / budget-exceeded) | P2 |
| No operational read endpoint for passive scan run status (useful for diagnostics, not required for V1 product) | P2 |
| Claim expiry and lease recovery events not logged at a distinguishable log level | P2 |
| Gateway routing failure for passive-awareness would not be detectable from application logs alone (silent 502) | P1 (addressed by F-01) |

---

## K. Frontend Integration Audit

| Check | Status |
|---|---|
| `GET /api/v1/passive-awareness` path | ✓ `/api/` + `v1/passive-awareness` = `/api/v1/passive-awareness` |
| Gateway route | ✓ `/api/v1/passive-awareness/**` → market-intelligence |
| `PassiveAwarenessResponse` DTO fields in Angular model | ✓ Matches backend `PassiveAwarenessResponse.from()` output |
| `CreateActiveScanRequest` passive fields | ✓ `sourcePassiveOpportunityId`, `sourcePassiveOpportunityVersion` added in Story 0244 |
| `Idempotency-Key` header on POST | ✓ `ActiveScanService.createScan()` passes header |
| Polling cancellation | ✓ `takeUntilDestroyed()` |
| Account selection enforcement | ✓ `canStart = selectedAccountId() !== '' && status === 'idle'` |
| Error code display | ✓ Angular `errorMessage(code)` maps known codes to labels |
| Loading / error / empty / loaded states | ✓ All 4 states + filtered-empty; 9 feed component tests |
| Navigation on OPPORTUNITY_FOUND | ✓ Routes to `/decision-workspace?opportunityId=…` |
| No auto scan on feed load | ✓ Safety regression test |
| Bundle budget | ⚠ 1.03 MB; warning threshold 700 kB already exceeded |

**No contract mismatch found between the Angular model and the backend response.** The scope constraint (`SELECTED` + `[candidateMarketId]`) in the POST body matches exactly what `PassiveCandidateScanValidator.scopeMatches()` requires.

---

## L. Epic Closure Criteria

The following must all be true before Epic 0238 is declared `V1_INTEGRATION_VALIDATED`:

| # | Criterion | Current status |
|---|---|---|
| C-01 | All six Stories (0239–0244) delivered to main | ✓ |
| C-02 | Story 0242 governance gap resolved: Implementation Report + independent review + human approval, OR explicitly accepted as residual risk in writing | ✗ Open — G-04-CRIT |
| C-03 | Stories 0240/0241 governance gaps resolved or accepted | ✗ Open — G-02-REV, G-03-REV |
| C-04 | Story 0244 status updated to reflect merge; engineering report created | ✗ Open — G-06-DOC |
| C-05 | Gateway routing integration test covers `/api/v1/passive-awareness` route (F-01) | ✗ Missing |
| C-06 | Deterministic fixture test: qualifying event → one candidate, non-qualifying → none, repeated identical → no flood (F-04 / R-0242-2) | ✗ Missing |
| C-07 | Story 0245 (cross-service E2E validation) implemented and human-approved, covering scenarios A–I | ✗ Proposed, not approved |
| C-08 | Authorization regression: passive candidate cannot bypass account eligibility | ✓ (unit test in 0243) — to be reproduced at cross-service level in C-07 |
| C-09 | Idempotency regression: same key → same scan; different candidate version → distinct fingerprint | ✓ (golden-hash test in 0243) — to be reproduced at cross-service level in C-07 |
| C-10 | Restart/recovery: lease expiry produces safe reconciliation without duplicate execution | ✓ (unit tests in 0241) — to be reproduced at cross-service level in C-07 |
| C-11 | No broker writes: confirmed in all test runs | ✓ ArchUnit + no broker adapter dependency |
| C-12 | No automatic trade execution from passive path | ✓ ArchUnit |
| C-13 | Observable failure diagnostics confirmed in fixture test | Partial (27 codes exist; no fixture test per F-04) |
| C-14 | Human approval of E2E validation evidence | ✗ Requires C-07 |

---

## M. Recommended Next Action

**Approve Story 0245** ("Validate Passive Market Scanning V1 End-to-End") as the next Engineering Story.

Before implementation begins:

1. Decide on the governance posture for Stories 0240, 0241, and 0242:
   - Accept existing review gaps as residual risks (document formally), or
   - Commission retrospective independent reviews.
2. Update Story 0244 `story.md` status to reflect merge (Q-01).
3. Create a Story 0244 engineering report (G-06-DOC).

Story 0245 implementation will close F-01, F-03, F-04, and C-05 through C-14, and provide the reproducible validation evidence required for epic closure.

---

## N. Readiness Verdict

**`READY_WITH_OPEN_DECISIONS`**

The six implementation milestones are delivered and the safety boundaries are confirmed by static analysis and unit tests. No P0 safety blocker is present. Three open decisions block epic closure:

1. **D-1 (required):** Governance posture for Stories 0240, 0241, 0242 (retrospective review or formal risk acceptance).
2. **D-2 (required):** Story 0245 approval and implementation.
3. **D-3 (optional):** Story 0244 status reconciliation and engineering report.

Epic 0238 must not be marked `V1_INTEGRATION_VALIDATED` until D-1 and D-2 are resolved.
