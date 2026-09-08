# Story 0035 — Implementation Report

## Baseline

```
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = story/0035-scoped-active-scan-results
HEAD_BEFORE = ceac98e
WORKTREE_BEFORE = CLEAN (untracked: 0035 story dir)
```

## Story

```
STORY = 0035-scope-active-market-scans-surface-per-market-results
STATUS = Implemented (Ready for Review)
```

## Backend Changes (market-intelligence)

### `TradingOpportunityRepository`

New query method `findByStrategyMatchIds(Collection<UUID>)` added to the port interface. Implementation via Spring Data JPQL on `strategyMatchId` column, enabling authoritative reconstruction of all opportunities per market from the `StrategyMatch → TradingOpportunity` lineage.

```java
List<TradingOpportunity> findByStrategyMatchIds(Collection<UUID> strategyMatchIds);
```

### `JpaTradingOpportunityRepository`

Implementation of the above query using `SELECT o FROM TradingOpportunity o WHERE o.strategyMatchId IN :strategyMatchIds`. No schema migration required — the `strategyMatchId` column already exists on the `trading_opportunity` table from prior Story 0013/0022 work.

### `ActiveScanReconciliationService`

Replaced single-opportunity lookup with multi-opportunity reconstruction per market:

1. For each `ActiveScanMarket`, load `analysisExecutionId`
2. Query `StrategyMatchRepository.findByAnalysisExecutionId()` → `List<StrategyMatch>`
3. Extract `matchIds`
4. Call `TradingOpportunityRepository.findByStrategyMatchIds(matchIds)` → `List<TradingOpportunity>`
5. Populate `ActiveScanResultProjection.MarketResult.opportunities` with the full list (zero, one, or many)

### `ActiveScanResultProjection.MarketResult`

Changed field from `opportunity: TradingOpportunity | null` to `opportunities: List<TradingOpportunity>`. Preserves zero-to-many cardinality per market as required by ADR-033 §Per-Market Result and Opportunity Cardinality.

### `ActiveScanResponse.MarketResponse`

REST contract change: `opportunity: OpportunityResponse | null` → `opportunities: OpportunityResponse[]`. Breaking change acceptable per Story 0035 atomic migration boundary (backend → REST → Angular consumer migrated together pre-V1).

## Frontend Changes (trading-os-web)

### `ActiveScanMarketResult` model

Changed `opportunity: OpportunityResponse | null` to `opportunities: OpportunityResponse[]`. All consumers updated to iterate over the array.

### Scope Selection UI

- **Specific markets**: trader multi-selects from market catalogue → `requestedMarketIds` sent to backend
- **All eligible markets**: explicit radio/button choice → `requestedMarketIds` omitted; backend resolves full catalog
- UI clearly labels the choice (distinct visual action for each mode)

### Per-Market Results Table

Compact filterable table with columns:
- Market identity (symbol, timeframe)
- Eligibility status (eligible / excluded)
- Exclusion reason (if not eligible)
- Analysis lifecycle state (RUNNING / COMPLETED / FAILED / COMPLETED_NO_SIGNAL)
- Outcome (OPPORTUNITY_FOUND / COMPLETED_NO_OPPORTUNITY / EXCLUDED / FAILED)
- Opportunity chips/links (navigate to `/opportunities/{id}`)

Filters: All | Opportunity produced | Completed no opportunity | Excluded | Failed | Still processing

### Objective Field

Labelled as "Provenance / context (does not influence scope, eligibility, or ranking)" — persisted on `ActiveScan.objective` but UI makes clear it is provenance-only.

### Opportunity Navigation

Each opportunity chip uses `[routerLink]` to `/opportunities/{opportunityId}`. Existing Opportunity detail route loads without duplicate rendering in scan panel.

## API Contract Change

Breaking atomic migration across three layers:

```
Backend projection          ↓
REST response contract      ↓
Angular model               ↓
Angular consumer
migrated together in Story 0035
```

- `POST /api/v1/intelligence/scans` request body unchanged (`requestedMarketIds` already exists)
- `GET /api/v1/intelligence/scans/{scanId}` response: `markets[].opportunity` → `markets[].opportunities[]`
- Each opportunity reference: `id`, `instrument`, `direction`, `scenario`, `timeframe`, `status`, `score`, `evaluatedAt`, `strategyMatchId`

## Behavioral Scenarios Covered

| Scenario | Description |
|---|---|
| **S01** | Specific single-market scan — one row, one opportunity chip (if any) |
| **S02** | Specific multi-market scan — one row per selected market |
| **S03** | Explicit all-eligible scan — UI indicates explicit choice, shows all eligible markets |
| **S04** | Requested market excluded by backend — excluded row with `MARKET_NOT_TRADABLE` reason |
| **S05** | Partial scan completion — filter "Still processing" shows running rows |
| **S06** | Completed scan with zero opportunities — "No setup matched" hint (not error) |
| **S07** | One opportunity — chip with instrument/direction/scenario/score |
| **S08** | Multiple opportunities for one market — all appear as separate chips, none hidden |
| **S09** | Opportunities across multiple markets — total count in progress footer |
| **S10** | Failed child analysis — failed row shows diagnostic; siblings remain visible |
| **S11** | Objective supplied — persisted, labelled provenance-only |
| **S12** | Objective omitted — normalized to `""`, scan proceeds normally |
| **S13** | Market catalogue unavailable — inline error "Unable to load markets" with Retry; "All eligible" remains functional |
| **S14** | Opportunity navigation — router navigates to `/opportunities/{opportunityId}` |
| **S15** | Market eligibility changes between selection and resolution — backend excludes with `MARKET_NOT_TRADABLE`; frontend does not override |

## Tests

| Module | Command | Passed | Failed |
|---|---|---|---|
| Market Intelligence | `mvn test` | 321 | 0 |
| Angular | `npm run test:ci` | 288 | 0 |
| Angular Build | `npm run build` | OK | 0 |
| `git diff --check` | — | Pass | — |

Note: Pre-existing budget warnings in Angular build are unrelated to Story 0035.

## Files Changed (relative to baseline)

**Market Intelligence (updated):**
- `TradingOpportunityRepository.java` — added `findByStrategyMatchIds`
- `JpaTradingOpportunityRepository.java` — implemented query
- `ActiveScanReconciliationService.java` — multi-opportunity reconstruction
- `ActiveScanResultProjection.MarketResult.java` — `opportunities: List<TradingOpportunity>`
- `ActiveScanResponse.MarketResponse.java` — `opportunities: OpportunityResponse[]`

**Angular (updated):**
- `active-scan.model.ts` — `opportunities: OpportunityResponse[]`
- `scan-panel.ts` — scope selection, filtering, navigation
- `scan-panel.html` — per-market table, chips, routerLink
- `scan-panel.scss` — table styling, chip appearance
- `scan-panel.spec.ts` — 22 behavioral tests

## Regression Review

```
STORY_0030_EXECUTION_FLOW = PRESERVED (no changes to execution path)
STORY_0033_POSITION_CLOSE = PRESERVED (no position close changes)
ACTIVE_SCAN_LIFECYCLE = PRESERVED (lifecycle semantics unchanged)
MARKET_CATALOGUE_PATH = UNCHANGED (reuse existing MarketService.findAll())
ACTIVE_SCAN_IDEMPOTENCY = PRESERVED (no idempotency changes)
BROKER_SERVICE = UNCHANGED (no broker order placement)
RISK_DOMAIN = UNCHANGED (risk evaluation unchanged)
NO_NEW_BROKER_PROVIDER = CONFIRMED (only Kraken infrastructure)
OUT_OF_SCOPE_FUNCTIONALITY = NONE INTRODUCED
  No passive scanner, no saved watchlists, no scheduled scanning
  No AI Engine, no News Service, no economic calendar
  No execution, no Risk changes, no Broker changes, no Kraken E2E
```

## Git

```
HEAD_AFTER = working tree changes (not committed)
STAGED = NO
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

## Implementation Result

```
STORY_0035_IMPLEMENTED_READY_FOR_REVIEW
```