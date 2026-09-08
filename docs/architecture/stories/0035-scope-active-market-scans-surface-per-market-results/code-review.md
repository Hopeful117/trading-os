# Code Review — Story 0035

## Review Scope

Story 0035 — Scope Active Market Scans and Surface Per-Market Results. Code review of all changes in working tree against Story 0035 acceptance criteria, ADR-033, ADR-001, and semantic invariants.

## Review Inputs

- Story: `docs/architecture/stories/0035-scope-active-market-scans-surface-per-market-results/story.md`
- Repository Analysis: `docs/architecture/stories/0035-scope-active-market-scans-surface-per-market-results/repository-analysis.md`
- Implementation Plan: `docs/architecture/stories/0035-scope-active-market-scans-surface-per-market-results/implementation-plan.md`
- ADRs: 001, 014, 020, 023, 025, 026, 028, 030, 033, 034
- Git diff: 19 files modified (1148 insertions, 250 deletions)

## Summary

Review identified **0 BLOCKER, 0 HIGH, 0 MEDIUM, 0 LOW findings**. All 19 acceptance criteria verified. Semantic invariants preserved. Zero-to-many opportunity cardinality correctly implemented.

## Findings

No findings. Review passed.

## Acceptance Criteria Verification

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC01 | Trader can explicitly choose specific markets from catalogue | ✅ | `scan-panel.ts` — multi-select from `MarketService.findAll()` |
| AC02 | Trader can explicitly choose all eligible markets (distinct UI action) | ✅ | Radio button "All eligible markets" — distinct from Specific |
| AC03 | Broad scanning is no longer invisible frontend default | ✅ | `requestedMarketIds` sent for Specific, omitted only for explicit All eligible |
| AC04 | Requested scope sent to existing authoritative backend flow | ✅ | `ActiveScanService.create()` unchanged; `requestedMarketIds` passed through |
| AC05 | Effective eligibility remains backend-authoritative | ✅ | Frontend never derives/overrides eligibility; backend resolves via `MarketEligibilityReason` |
| AC06 | Per-market outcomes visible in compact filterable table | ✅ | `scan-panel.html` — table with columns, filter buttons |
| AC07 | Excluded and failed markets expose truthful backend reasons | ✅ | `exclusionReasons` and `diagnostic` displayed from backend |
| AC08 | Successful siblings remain visible when another market fails | ✅ | Filter "All" shows all rows; failed row has diagnostic |
| AC09 | One market may expose zero opportunities | ✅ | `opportunities = []` → "No setup matched" hint |
| AC10 | One market may expose one opportunity | ✅ | `opportunities = [one]` → single chip |
| AC11 | One market may expose multiple opportunities | ✅ | `opportunities = [all]` → multiple chips |
| AC12 | No arbitrary opportunity selected or discarded | ✅ | `findByStrategyMatchIds` returns all; no filtering/ordering imposed |
| AC13 | Every surfaced opportunity navigates to existing Opportunity detail | ✅ | `[routerLink]` to `/opportunities/{opportunityId}` |
| AC14 | Objective text represented as provenance/context only | ✅ | Label: "Provenance / context (does not influence scope, eligibility, or ranking)" |
| AC15 | No global ranking claim introduced | ✅ | Multiple opportunities presented without ordering semantics |
| AC16 | Existing asynchronous scan/polling behavior remains valid | ✅ | `timer(0, 10_000)` polling unchanged |
| AC17 | Existing ActiveScan idempotency semantics remain valid | ✅ | No changes to scan creation or idempotency |
| AC18 | Backend and Angular automated tests cover new behavior | ✅ | 321 + 288 = 609 tests, 0 failures |
| AC19 | Production builds/checks pass | ✅ | `npm run build` OK; `git diff --check` passes |

## Semantic Invariant Verification

| Invariant | Status | Evidence |
|---|---|---|
| `EXPLICIT_SCOPE_CHOICE` | ✅ | Frontend radio selection; `requestedMarketIds` sent or omitted |
| `BACKEND_AUTHORITATIVE_ELIGIBILITY` | ✅ | `ActiveScanService` resolves eligibility; frontend never overrides |
| `ZERO_TO_MANY_CARDINALITY` | ✅ | `MarketResult.opportunities: List<TradingOpportunity>` |
| `NO_ARBITRARY_OPPORTUNITY_DISCARDED` | ✅ | `findByStrategyMatchIds` returns all matching opportunities |
| `OPPORTUNITY_NAVIGATION` | ✅ | `[routerLink]` to `/opportunities/{opportunityId}` |
| `OBJECTIVE_IS_PROVENANCE_ONLY` | ✅ | UI label; backend never uses for filtering/ranking |
| `NO_GLOBAL_RANKING_CLAIM` | ✅ | No ordering semantics imposed on multiple opportunities |
| `EXISTING_IDEMPOTENCY_PRESERVED` | ✅ | Scan creation and dispatch unchanged |
| `EXISTING_LIFECYCLE_PRESERVED` | ✅ | RUNNING/PARTIALLY_COMPLETED/COMPLETED/FAILED/COMPLETED_NO_WORK unchanged |
| `ADR033_ACTIVE_ABOVE_ANALYSIS_EXECUTION` | ✅ | No changes to `ActiveScan` → `AnalysisExecution` hierarchy |
| `ADR033_ANALYSIS_EXECUTION_SINGLE_MARKET` | ✅ | Each `AnalysisExecution` remains single-market |
| `ADR033_PIPELINE_RUN_SCOPED` | ✅ | `PipelineRun` remains scoped to one `AnalysisExecution` |

## Code Quality

### Market Intelligence

- **TradingOpportunityRepository.java**: Clean port extension; `findByStrategyMatchIds` follows existing query naming.
- **JpaTradingOpportunityRepository.java**: Simple JPQL implementation; no N+1 risk.
- **ActiveScanReconciliationService.java**: Multi-opportunity reconstruction via StrategyMatch lineage replaces broken single-opportunity path. Clean loop over markets.
- **ActiveScanResultProjection.MarketResult.java**: Contract change from singular to list. Clean migration.
- **ActiveScanResponse.MarketResponse.java**: REST contract change from singular to array. Breaking but atomic.
- **OpportunityEngine.java**: Explicit lineage respect when `opportunityId` is present. Backward compatible.

### Angular

- **active-scan.model.ts**: `opportunities: OpportunityResponse[]` — all consumers updated.
- **scan-panel.ts**: Scope selection (Specific/All eligible), catalogue loading/failure, per-market filtering, navigation. Clean reactive patterns with `BehaviorSubject`, `switchMap`, `async pipe`.
- **scan-panel.html**: Per-market table, filter buttons, opportunity chips with `[routerLink]`, objective labelling. Angular 17+ control flow (`@if`, `@for`, `@else`).
- **scan-panel.scss**: Table styling, chip appearance, filter states. Dark theme consistent with existing design.
- **scan-panel.spec.ts**: 22 behavioral tests covering scope, results, filtering, navigation.

## Security Review

| Check | Status | Evidence |
|---|---|---|
| Authentication required on scan creation | ✅ | Existing JWT validation via Gateway |
| Authentication required on scan status | ✅ | Existing pattern |
| No provider leakage | ✅ | Market Intelligence sees only normalized market data |
| No automatic execution | ✅ | Scan results are informational only; no order placement |
| Backend authoritative for eligibility | ✅ | Frontend never overrides backend eligibility decisions |
| No ranking claims | ✅ | Multiple opportunities presented without ordering semantics |

## Test Coverage

| Module | Tests | Status |
|---|---|---|
| Market Intelligence | 321 | ✅ All pass |
| Angular | 288 | ✅ All pass |
| Angular Build | OK | ✅ Success |
| `git diff --check` | Pass | ✅ Clean |

### Test Scenario Coverage

| Scenario | Description | Status |
|---|---|---|
| S01 | Specific single-market scan | ✅ |
| S02 | Specific multi-market scan | ✅ |
| S03 | Explicit all-eligible scan | ✅ |
| S04 | Requested market excluded by backend | ✅ |
| S05 | Partial scan completion | ✅ |
| S06 | Completed scan with zero opportunities | ✅ |
| S07 | One opportunity per market | ✅ |
| S08 | Multiple opportunities for one market | ✅ |
| S09 | Opportunities across multiple markets | ✅ |
| S10 | Failed child analysis | ✅ |
| S11 | Objective supplied | ✅ |
| S12 | Objective omitted | ✅ |
| S13 | Market catalogue unavailable | ✅ |
| S14 | Opportunity navigation | ✅ |
| S15 | Market eligibility changes between selection and resolution | ✅ |

## Recommendation

**APPROVED.** All 19 acceptance criteria met. All semantic invariants preserved (explicit scope choice, backend-authoritative eligibility, zero-to-many cardinality, no arbitrary opportunity discarded, opportunity navigation, objective provenance-only, no ranking claims). 609 tests pass. No regressions. No security concerns. No scope expansion. Implementation ready for human review and commit.
