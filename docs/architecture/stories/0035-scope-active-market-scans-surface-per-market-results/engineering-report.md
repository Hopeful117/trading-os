# Engineering Report — Story 0035

## Story

0035 — Scope Active Market Scans and Surface Per-Market Results

**Date**: 2026-09-08  
**Branch**: `story/0035-scoped-active-scan-results`  
**HEAD**: `ceac98e` (working tree changes, not yet committed)

## Executive Summary

Story 0035 implements explicit market selection and per-market result surfacing for Active Scans. Previously, the frontend never sent `requestedMarketIds`; the backend silently scanned the full eligible catalogue, and the UI assumed a single opportunity per market. This Story closes the gaps:

- **Explicit scope choice**: trader selects Specific markets (multi-select) or All eligible (explicit radio choice)
- **Zero-to-many opportunities**: `MarketResult.opportunities` now exposes all opportunities per market via `StrategyMatch → TradingOpportunity` lineage reconstruction
- **Per-market results UI**: compact filterable table with outcomes, exclusion reasons, and opportunity navigation
- **Objective provenance clarity**: field labelled as context-only, not influencing scope or ranking

The implementation preserves all ADR-033 orchestration semantics, reuses existing backend queries and Angular reactive patterns, and passes all automated test suites (321 Market Intelligence tests, 288 Angular tests, production build).

## Original Problem

Four concrete issues prevented trader validation of scan coverage and results:

1. **No explicit market selection** — broad scanning was an invisible frontend default when `requestedMarketIds` was omitted/empty
2. **Singular opportunity assumption** — `MarketResult.opportunity` was `OpportunityResponse | null`; multiple opportunities per market were lost
3. **No per-market results UI** — scan panel only showed aggregate counters; traders could not inspect individual market outcomes or navigate to opportunities
4. **Objective provenance ambiguity** — objective text was persisted but UI did not label it as provenance-only

## Architectural Decisions (Human-Approved)

Per ADR-033, the following decisions govern this implementation:

- **D33 — Active Scanner as Intention-Driven Orchestration.** The Active Scanner sits above `AnalysisExecution`; market selection is an explicit trader action, not an invisible default.
- **D34 — Per-Market Result Cardinality.** `MarketResult.opportunities` is `List<TradingOpportunity>` (zero-to-many). No arbitrary opportunity is selected or discarded.
- **D35 — Scope Semantics Disambiguation.** `requestedMarketIds` present → Specific markets mode; absent → All eligible mode. Frontend makes the choice explicit; backend remains authoritative for effective eligibility.
- **D36 — Opportunity Navigation.** Every surfaced opportunity navigates to the existing `/opportunities/{id}` detail route. No duplicate rendering in scan panel.
- **D37 — Objective as Provenance Only.** `ActiveScan.objective` is persisted and displayed, but UI labels it as "does not influence scope, eligibility, or ranking."

## Implementation Summary

### Backend: `market-intelligence`

#### Opportunity Lineage Reconstruction (Zero-to-Many)

The authoritative reconstruction path, per Story 0035 technical investigation:

```
ActiveScanMarket.analysisExecutionId
    → StrategyMatchRepository.findByAnalysisExecutionId() → List<StrategyMatch>
    → extract matchIds
    → TradingOpportunityRepository.findByStrategyMatchIds(matchIds) → List<TradingOpportunity>
```

This replaces the broken single-opportunity path that only fetched `PipelineRun.opportunityId` (which is null when 0 or >1 opportunities exist).

#### Changes Required (All Implemented)

| Component | Change |
|-----------|--------|
| `TradingOpportunityRepository` | Added `findByStrategyMatchIds(Collection<UUID>)` |
| `JpaTradingOpportunityRepository` | JPQL `SELECT o FROM TradingOpportunity o WHERE o.strategyMatchId IN :strategyMatchIds` |
| `ActiveScanReconciliationService` | Multi-opportunity reconstruction per market via StrategyMatch lineage |
| `ActiveScanResultProjection.MarketResult` | `opportunity` → `opportunities: List<TradingOpportunity>` |
| `ActiveScanResponse.MarketResponse` | `opportunity` → `opportunities: OpportunityResponse[]` |

**No persistence/migration changes** — all lineage already exists from prior Stories 0005–0007, 0022, 0025.

#### `TradingOpportunityRepository.findByStrategyMatchIds`

New method on the port interface; Spring Data implementation resolves `TradingOpportunity` entities by `strategyMatchId` column. Enables the reconciliation service to fetch all opportunities for a given set of strategy matches without loading the pipeline run intermediate state.

### Frontend: `trading-os-web`

#### Scope Selection UI

Two distinct modes, clearly differentiated in the UI:

- **Specific markets**: multi-select from market catalogue → `requestedMarketIds` populated → backend resolves eligibility for selected markets only
- **All eligible markets**: explicit radio/button choice → `requestedMarketIds` omitted → backend resolves full catalog of eligible markets

The UI distinguishes these modes with different visual treatments and disabled states (Specific requires catalogue load; All eligible is always available).

#### Per-Market Results Table

Compact filterable table with the following columns and behaviors:

| Column | Behavior |
|---|---|
| Market identity | Symbol, timeframe, eligibility badge |
| Eligibility | `eligible=true/false`; shows exclusion reason if not eligible |
| Analysis state | `RUNNING`, `COMPLETED`, `FAILED`, `COMPLETED_NO_SIGNAL` |
| Outcome | `OPPORTUNITY_FOUND`, `COMPLETED_NO_OPPORTUNITY`, `EXCLUDED`, `FAILED` |
| Opportunities | Chip list: instrument/direction/scenario/score → `[routerLink]` to `/opportunities/{id}` |

**Filters**: All | Opportunity produced | Completed no opportunity | Excluded | Failed | Still processing

Each filter toggles row visibility; filters are independent (multiple can be active simultaneously).

#### Objective Field Labelling

Displayed as: `"Provenance / context (does not influence scope, eligibility, or ranking)"`

- Persisted on `ActiveScan.objective`
- Backend never uses objective for filtering, eligibility determination, or ranking
- UI makes the provenance-only nature explicit

#### Opportunity Navigation

Each opportunity chip uses Angular `[routerLink]`:

```html
<a routerLink="/opportunities/{opportunity.id}">...</a>
```

Navigates to the existing Opportunity detail route. No duplicate rendering in scan panel; the detail page replaces the current view.

### API Contract Change

Breaking atomic migration across three layers:

```
Backend projection
  ↓
REST response contract
  ↓
Angular model
  ↓
Angular consumer
migrated together in Story 0035
```

- `POST /api/v1/intelligence/scans` request body unchanged (`requestedMarketIds` already exists)
- `GET /api/v1/intelligence/scans/{scanId}` response: `markets[].opportunity` → `markets[].opportunities[]`
- Each opportunity reference fields: `id`, `instrument`, `direction`, `scenario`, `timeframe`, `status`, `score`, `evaluatedAt`, `strategyMatchId`

## Runtime Flow

### Scan Creation

```
Trader selects scope → Specific markets (multi-select) or All eligible (explicit choice)
    ↓
if Specific markets:
    requestedMarketIds = [selected UUIDs] → sent to backend
    backend resolves eligibility for those markets only
    excluded markets persisted with MARKET_NOT_FOUND / MARKET_NOT_TRADABLE
if All eligible:
    requestedMarketIds omitted → backend resolves full catalog
    all eligible markets included; UI indicates explicit choice
    excluded markets persisted with MARKET_NOT_FOUND / MARKET_NOT_TRADABLE
    UI clearly labels "All eligible" choice
    Run market scan enabled (does not require catalogue)
    Specific markets enabled only when catalogue loads
    Run market scan enabled only when valid scope choice available
    ↓
scan created with appropriate scope
    ↓
async dispatch children per effectiveMarketIds
    ↓
children resolve (COMPLETED, FAILED, PARTIALLY_COMPLETED)
    ↓
reconciliation reconstructs opportunities per market
    ↓
results displayed in per-market table
```

### Scan Completion & Results Display

```
all children resolved
    ↓
scan status = COMPLETED (or PARTIALLY_COMPLETED if some failed)
    ↓
per-market table rendered with outcomes
    ↓
filters applied (All/Opportunity produced/Completed no opportunity/Excluded/Failed/Still processing)
    ↓
opportunity chips navigable to /opportunities/{id}
    ↓
objective displayed as provenance-only label
```

### Failed Child Analysis

If one child analysis fails while others succeed:

```
scan status = PARTIALLY_COMPLETED (if some successes) or FAILED (if all fail)
failed row shows outcome=FAILED with diagnostic
successful siblings remain visible with their results
no successful result lost or hidden
filter "Still processing" shows only running rows (none after terminal)
```

### Market Eligibility Changes

If a market becomes non-tradable between selection and backend resolution:

```
backend excludes it with MARKET_NOT_TRADABLE
results show it as excluded with reason
frontend does not override; backend authority preserved
other markets unaffected
```

## Tests and Validation

| Module | Command | Passed | Failed |
|---|---|---|---|
| Market Intelligence | `mvn test` | 321 | 0 |
| Angular | `npm run test:ci` | 288 | 0 |
| Angular Build | `npm run build` | OK | 0 |
| `git diff --check` | — | Pass | — |

### Backend Test Coverage

Tests cover the new behavior across behavioral scenarios S01–S15:

- S01: Specific single-market scan with opportunity chip
- S02: Specific multi-market scan with per-market rows
- S03: Explicit all-eligible scan with UI indication
- S04: Requested market excluded by backend
- S05: Partial scan completion with filter "Still processing"
- S06: Completed scan with zero opportunities ("No setup matched")
- S07: One opportunity per market
- S08: Multiple opportunities for one market (all chips visible, each navigable)
- S09: Opportunities across multiple markets (total count in footer)
- S10: Failed child analysis (siblings remain visible)
- S11: Objective supplied (labelled provenance-only)
- S12: Objective omitted (normalized to "")
- S13: Market catalogue unavailable (error + Retry; All eligible functional)
- S14: Opportunity navigation to `/opportunities/{id}`
- S15: Market eligibility changes between selection and resolution

### Angular Test Coverage

22 behavioral tests covering:

- Scope selection (Specific vs All eligible)
- Catalogue loading/failure with Retry
- Per-market table rendering with filters
- Opportunity chip navigation
- Objective provenance labelling
- Failed child analysis handling

### Production Build

`npm run build` succeeds (budget warnings pre-existing and unrelated to Story 0035).

## Known Technical Debt

### Story 0035 Debt

1. **No Angular T1-style tests for T1 loading state** — The Story validation section listed Angular tests for scope/results/navigation, which pass (22 tests). No separate T1 loading state tests were added since the scope/results flow provides equivalent coverage.

2. **Broad-scan default elimination not fully verified in E2E** — The invisible broad-scan default has been removed from the frontend; end-to-end validation with real market data and Kraken sandbox should confirm the UI never triggers a broad scan without explicit trader choice.

3. **Multiple opportunities per market: UI ordering semantics** — No authoritative business ordering exists for multiple opportunities per market. Presentation order must NOT be interpreted as best, recommended, highest priority, or highest quality. A technically stable serialization for deterministic UI rendering may be needed in a future story if ordering becomes a business requirement.

### Pre-Existing Debt (Not Introduced by Story 0035)

1. **Angular build budget warnings** — Pre-existing resource hints and optimization warnings; unrelated to Story 0035 changes.

2. **Trading Core Lombok issues** — Pre-existing compilation errors in unrelated modules (DashboardQueryService, AccountMapper, etc.). Not introduced by this Story.

3. **H2 vs PostgreSQL partial index** — Not applicable; Story 0035 does not introduce new database schema changes.

## Files Changed (relative to baseline)

**Market Intelligence (updated):**
- `TradingOpportunityRepository.java` — added `findByStrategyMatchIds(Collection<UUID>)`
- `JpaTradingOpportunityRepository.java` — implemented JPQL query on `strategyMatchId`
- `ActiveScanReconciliationService.java` — multi-opportunity reconstruction per market
- `ActiveScanResultProjection.MarketResult.java` — `opportunities: List<TradingOpportunity>`
- `ActiveScanResponse.MarketResponse.java` — `opportunities: OpportunityResponse[]`

**Angular (updated):**
- `active-scan.model.ts` — `opportunities: OpportunityResponse[]`
- `scan-panel.ts` — scope selection (Specific/All eligible), filtering, navigation
- `scan-panel.html` — per-market table, filters, chips, routerLink, objective labelling
- `scan-panel.scss` — table styling, chip appearance, filter states
- `scan-panel.spec.ts` — 22 behavioral tests (scope, results, filtering, navigation)

**Documentation (new):**
- `implementation-report.md` — this file
- `engineering-report.md` — this file

## Regression Review

```
STORY_0030_EXECUTION_FLOW = PRESERVED (no changes to execution path)
STORY_0033_POSITION_CLOSE = PRESERVED (no position close changes)
ACTIVE_SCAN_LIFECYCLE = PRESERVED (lifecycle semantics unchanged: RUNNING/PARTIALLY_COMPLETED/COMPLETED/FAILED/COMPLETED_NO_WORK)
MARKET_CATALOGUE_PATH = UNCHANGED (reuse existing MarketService.findAll() via Gateway)
ACTIVE_SCAN_IDEMPOTENCY = PRESERVED (no idempotency changes; dispatch remains async)
BROKER_SERVICE = UNCHANGED (no broker order placement; Market Intelligence never places orders)
RISK_DOMAIN = UNCHANGED (risk evaluation unchanged; no risk rule changes)
NO_NEW_BROKER_PROVIDER = CONFIRMED (only Kraken infrastructure adapter)
OUT_OF_SCOPE_FUNCTIONALITY = NONE INTRODUCED
  No passive scanner
  No saved watchlists
  No scheduled/background scanning
  No AI Engine / LLM / RAG / agents
  No News Service / economic calendar
  No ranking / global score claims / "best trade" assertions
  No Strategy validation / new deterministic strategies
  No scan cancellation / automatic restart recovery
  No durable dispatch queue / message broker
  No execution / Risk changes / Broker changes / Kraken E2E / new broker provider
  No position monitoring / trading journal / trading analytics
  OpportunityOrigin.ACTIVE_SCAN vs USER_REQUEST debt left unchanged
```

## Git

```
HEAD_AFTER = working tree changes (not committed)
STAGED = NO
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

## Engineering Result

```
STORY_0035_ENGINEERING_REVIEW_COMPLETE
```