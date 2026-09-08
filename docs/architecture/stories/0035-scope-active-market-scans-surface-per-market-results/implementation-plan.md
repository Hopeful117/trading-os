# Implementation Plan — Story 0035

## Design

```
[Phase A]  Backend: TradingOpportunityRepository.findByStrategyMatchIds
[Phase B]  Backend: ActiveScanReconciliationService multi-opportunity reconstruction
[Phase C]  Backend: ActiveScanResultProjection + ActiveScanResponse contract change
[Phase D]  Backend: OpportunityEngine explicit lineage respect
[Phase E]  Frontend: ActiveScanMarketResult model update
[Phase F]  Frontend: Scope selection UI (Specific markets / All eligible)
[Phase G]  Frontend: Per-market results table with filters
[Phase H]  Frontend: Opportunity chips with navigation
[Phase I]  Frontend: Objective provenance labelling
[Phase J]  Tests + Validation
[Phase K]  Story Artifacts
```

## Critical Sequence (ADR-033)

Every active scan MUST follow this exact sequence:

```
Trader chooses scope
    ↓
Specific markets: requestedMarketIds = [selected UUIDs]
All eligible: requestedMarketIds omitted
    ↓
POST /api/v1/intelligence/scans
    ↓
ActiveScanService resolves scope (authoritative backend)
    ↓
Dispatches one AnalysisExecution per eligible market
    ↓
ProductionIntelligencePipeline per market
    → observations → capabilities → StrategyEvaluation → StrategyMatch → TradingOpportunity
    ↓
ActiveScanReconciliationService.reconcileOwned()
    → StrategyMatchRepository.findByAnalysisExecutionId() → List<StrategyMatch>
    → TradingOpportunityRepository.findByStrategyMatchIds() → List<TradingOpportunity>
    → MarketResult.opportunities = all per market (0..N)
    ↓
GET /api/v1/intelligence/scans/{scanId}
    → markets[].opportunities[] (array, zero-to-many)
    ↓
Angular: per-market table, filters, opportunity chips → /opportunities/{id}
```

**Invariants enforced:**

| Invariant | Enforcement Point |
|---|---|
| Explicit scope choice (no invisible default) | Frontend radio selection; `requestedMarketIds` sent or omitted |
| Backend authoritative for effective eligibility | `ActiveScanService` resolves eligibility; frontend never overrides |
| Zero-to-many opportunity cardinality | `MarketResult.opportunities: List<TradingOpportunity>` |
| No arbitrary opportunity discarded | All StrategyMatch → TradingOpportunity lineage preserved |
| Opportunity navigation to existing detail | `[routerLink]` to `/opportunities/{id}` |
| Objective is provenance-only | UI label; backend never uses for filtering/ranking |
| No global ranking claim | Multiple opportunities have no authoritative ordering |
| Existing idempotency, dispatch, lifecycle preserved | No changes to scan creation, dispatch, or lifecycle semantics |

---

## Phase A — Backend: TradingOpportunityRepository.findByStrategyMatchIds

### Objective

Add query method to `TradingOpportunityRepository` port interface and implement via Spring Data JPQL.

### Existing Components to Reuse

- `TradingOpportunityRepository` port interface
- `JpaTradingOpportunityRepository` Spring Data implementation
- `strategyMatchId` column already exists on `trading_opportunity` table

### Files to Modify

| File | Change |
|---|---|
| `TradingOpportunityRepository.java` | Add `List<TradingOpportunity> findByStrategyMatchIds(Collection<UUID> strategyMatchIds)` |
| `JpaTradingOpportunityRepository.java` | Implement via `SELECT o FROM TradingOpportunity o WHERE o.strategyMatchId IN :strategyMatchIds` |
| `InMemoryTradingOpportunityRepository.java` | Add in-memory implementation for tests |
| `SpringDataTradingOpportunityRepository.java` | Spring Data query derivation |

### Invariants

- No schema migration required (strategyMatchId column exists)
- Returns empty list when no matches found (not null)
- Preserves all matching opportunities (no limit/ordering imposed)

### Tests Required Before Proceeding

1. `findByStrategyMatchIds` with empty collection → empty list
2. `findByStrategyMatchIds` with one match → one opportunity
3. `findByStrategyMatchIds` with multiple matches → all opportunities
4. `findByStrategyMatchIds` with non-existent IDs → empty list

### Dependencies

None. Foundation for all subsequent phases.

---

## Phase B — Backend: ActiveScanReconciliationService Multi-Opportunity Reconstruction

### Objective

Replace single-opportunity lookup with multi-opportunity reconstruction per market using StrategyMatch lineage.

### Existing Components to Reuse

- `ActiveScanReconciliationService.reconcileOwned()` current logic
- `StrategyMatchRepository.findByAnalysisExecutionId()`
- `TradingOpportunityRepository.findByStrategyMatchIds()` (Phase A)

### Current Logic (Broken for 0..N)

```java
// loads PipelineRun per AnalysisExecution
// collects opportunityId where non-null
// fetches via TradingOpportunityRepository.findAllExact(refs)
// successWithOpportunity(): looks up ONE opportunity via pipelineRun.opportunityId()
```

### New Logic

```java
for each ActiveScanMarket:
    analysisExecutionId = market.analysisExecutionId()
    strategyMatches = strategyMatchRepository.findByAnalysisExecutionId(analysisExecutionId)
    matchIds = strategyMatches.stream().map(StrategyMatch::matchId).toList()
    opportunities = tradingOpportunityRepository.findByStrategyMatchIds(matchIds)
    marketResult.opportunities = opportunities  // 0..N
```

### Invariants

- Zero opportunities: `opportunities = []` (not null)
- One opportunity: `opportunities = [that opportunity]`
- N>1 opportunities: `opportunities = [all N]`
- No opportunity hidden or discarded
- Presentation order MUST NOT be interpreted as ranking

### Tests Required Before Proceeding

1. Zero opportunities → empty list
2. One opportunity → single-element list
3. Multiple opportunities → all preserved
4. Mixed markets (some with 0, some with N) → correct per-market

### Dependencies

Phase A (findByStrategyMatchIds).

---

## Phase C — Backend: ActiveScanResultProjection + ActiveScanResponse Contract Change

### Objective

Update projection and REST response to expose `opportunities[]` array instead of singular `opportunity`.

### Files to Modify

| File | Change |
|---|---|
| `ActiveScanResultProjection.MarketResult` | `opportunity` → `opportunities: List<TradingOpportunity>` |
| `ActiveScanResponse.MarketResponse` | `opportunity` → `opportunities: OpportunityResponse[]` |

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

- `GET /api/v1/intelligence/scans/{scanId}` response: `markets[].opportunity` → `markets[].opportunities[]`
- Each opportunity reference: `id`, `instrument`, `direction`, `scenario`, `timeframe`, `status`, `score`, `evaluatedAt`, `strategyMatchId`

### Invariants

- Breaking change acceptable pre-V1
- Both backend and Angular migrated together
- No API versioning or compatibility alias introduced

### Tests Required Before Proceeding

1. API response includes `opportunities[]` array
2. Empty array for zero opportunities
3. Single-element array for one opportunity
4. Multi-element array for multiple opportunities

### Dependencies

Phase B (reconstruction populates opportunities).

---

## Phase D — Backend: OpportunityEngine Explicit Lineage Respect

### Objective

Ensure `OpportunityEngine` respects explicit `opportunityId` lineage when available, rather than always reconstructing from StrategyMatch.

### Files to Modify

| File | Change |
|---|---|
| `OpportunityEngine.java` | When `opportunityId` is present on PipelineRun, use it directly; otherwise reconstruct via StrategyMatch lineage |

### Invariants

- Explicit `opportunityId` takes precedence (backward compatibility)
- Reconstruction only occurs when `opportunityId` is null
- Existing PipelineRun behavior preserved

### Tests Required Before Proceeding

1. PipelineRun with explicit opportunityId → uses that opportunity
2. PipelineRun with null opportunityId → reconstructs via StrategyMatch
3. PipelineRun with null opportunityId and no matches → empty list

### Dependencies

Phase A (findByStrategyMatchIds for reconstruction path).

---

## Phase E — Frontend: ActiveScanMarketResult Model Update

### Objective

Update Angular model to match new backend contract.

### Files to Modify

| File | Change |
|---|---|
| `active-scan.model.ts` | `opportunity: OpportunityResponse \| null` → `opportunities: OpportunityResponse[]` |

### Invariants

- All consumers updated to iterate over array
- Empty array for zero opportunities
- No null check needed (always an array)

### Tests Required Before Proceeding

1. Model accepts array response
2. Empty array renders correctly
3. Single-element array renders correctly
4. Multi-element array renders correctly

### Dependencies

Phase C (backend contract).

---

## Phase F — Frontend: Scope Selection UI

### Objective

Add explicit scope selection: Specific markets (multi-select) vs All eligible markets (explicit radio choice).

### Existing Components to Reuse

- `MarketService.findAll()` for catalogue loading
- `scan-panel.ts` reactive state
- `scan-panel.html` template structure

### Files to Modify

| File | Change |
|---|---|
| `scan-panel.ts` | Add scope selection state, catalogue loading, market multi-select |
| `scan-panel.html` | Add radio buttons (Specific/All eligible), multi-select dropdown, catalogue error/retry |
| `scan-panel.scss` | Scope selection styling, disabled states |

### UX Flow

```
Trader opens scan panel
    ↓
Scope selection:
  ○ Specific markets (requires catalogue load)
  ○ All eligible markets (always available)
    ↓
if Specific markets:
  → Load catalogue via MarketService.findAll()
  → If catalogue fails: inline error "Unable to load markets" + Retry button
  → If catalogue loads: multi-select dropdown
  → "Run market scan" enabled only when markets selected
if All eligible:
  → "Run market scan" enabled immediately
  → No catalogue dependency
    ↓
POST /api/v1/intelligence/scans
  → Specific: requestedMarketIds = [selected UUIDs]
  → All eligible: requestedMarketIds omitted
```

### Invariants

- Broad scanning is no longer invisible default
- All eligible is explicit choice (distinct UI action)
- Catalogue failure does not block All eligible
- "Run market scan" enabled only when valid scope choice available
- Objective field labelled as provenance-only

### Tests Required Before Proceeding

1. Scope selection renders with both options
2. Specific markets requires catalogue load
3. Catalogue failure shows error + Retry
4. All eligible works without catalogue
5. "Run market scan" disabled until valid scope chosen

### Dependencies

Phase E (model update).

---

## Phase G — Frontend: Per-Market Results Table with Filters

### Objective

Display per-market outcomes in a compact filterable table.

### Files to Modify

| File | Change |
|---|---|
| `scan-panel.ts` | Add filtering logic, market result rendering |
| `scan-panel.html` | Add table with columns, filter buttons |
| `scan-panel.scss` | Table styling, filter states |

### Table Columns

| Column | Behavior |
|---|---|
| Market identity | Symbol, timeframe |
| Eligibility | eligible / excluded badge |
| Exclusion reason | MARKET_NOT_FOUND / MARKET_NOT_TRADABLE |
| Analysis state | RUNNING / COMPLETED / FAILED / COMPLETED_NO_SIGNAL |
| Outcome | OPPORTUNITY_FOUND / COMPLETED_NO_OPPORTUNITY / EXCLUDED / FAILED |
| Opportunities | Chip list (Phase H) |

### Filters

- All
- Opportunity produced
- Completed no opportunity
- Excluded
- Failed
- Still processing

### Invariants

- One row per market (requested or effective)
- Excluded markets show backend exclusion reasons
- Failed markets show diagnostic
- Successful siblings remain visible when another market fails
- Filter "Still processing" shows only running rows
- No duplicate rendering

### Tests Required Before Proceeding

1. Table renders one row per market
2. Excluded market shows reason
3. Failed market shows diagnostic
4. Filters toggle row visibility
5. "Still processing" shows only running rows

### Dependencies

Phase E (model), Phase H (chips).

---

## Phase H — Frontend: Opportunity Chips with Navigation

### Objective

Display zero-to-many opportunity chips per market, each navigating to existing `/opportunities/{id}` detail.

### Files to Modify

| File | Change |
|---|---|
| `scan-panel.html` | Add opportunity chips with `[routerLink]` |
| `scan-panel.scss` | Chip styling |

### UX Flow

```
Market row opportunities column:
    ↓
if opportunities.length === 0:
  → "No setup matched" hint (not error)
if opportunities.length === 1:
  → Single chip: instrument / direction / scenario / score
if opportunities.length > 1:
  → Multiple chips: each instrument / direction / scenario / score
    ↓
Click chip → router navigates to /opportunities/{opportunityId}
    ↓
Existing Opportunity detail loads
```

### Invariants

- Every surfaced opportunity navigates to existing detail
- No duplicate rendering in scan panel
- No opportunity hidden
- Click opens existing route (not new tab)
- Opportunity chips are non-interactive during scan (RUNNING state)

### Tests Required Before Proceeding

1. Zero opportunities → "No setup matched"
2. One opportunity → single chip with correct data
3. Multiple opportunities → all chips visible
4. Click chip → correct route navigation
5. Chips non-interactive during RUNNING

### Dependencies

Phase E (model), Phase G (table).

---

## Phase I — Frontend: Objective Provenance Labelling

### Objective

Label objective field as provenance-only in the UI.

### Files to Modify

| File | Change |
|---|---|
| `scan-panel.html` | Update objective field label |

### Label

```
"Provenance / context (does not influence scope, eligibility, or ranking)"
```

### Invariants

- Label clearly communicates provenance-only nature
- Backend never uses objective for filtering, eligibility, or ranking
- Objective persisted on `ActiveScan.objective`

### Tests Required Before Proceeding

1. Label displays correctly
2. Objective value persisted in scan creation

### Dependencies

None (independent of other phases).

---

## Phase J — Tests + Validation

### Objective

Validate no regressions and pass quality pipeline.

### Validation Commands

| Scope | Command |
|---|---|
| Market Intelligence | `mvn test` in `market-intelligence/` |
| Angular tests | `npm run test:ci` in `trading-os-web/` |
| Angular build | `npm run build` in `trading-os-web/` |
| Git diff | `git diff --check` |

### Regression Checklist

- [ ] Story 0005 scope resolution preserved
- [ ] Story 0022 scan trigger preserved
- [ ] Story 0025 async dispatch preserved
- [ ] ActiveScan lifecycle semantics preserved (RUNNING/PARTIALLY_COMPLETED/COMPLETED/FAILED/COMPLETED_NO_WORK)
- [ ] ActiveScan idempotency preserved
- [ ] Market catalogue path unchanged
- [ ] No execution/Risk/Broker changes
- [ ] No out-of-scope functionality introduced

### Dependencies

All previous phases.

---

## Phase K — Story Artifacts

### Objective

Complete story documentation lifecycle.

### Files to Create

| File | Content |
|---|---|
| `implementation-report.md` | Evidence of implementation: files changed, tests executed, behavioral scenarios covered |
| `engineering-report.md` | Architecture, semantic decisions, test results, known limitations |
| `code-review.md` | Acceptance criteria verification, semantic invariants, security review |

### Dependencies

Phase J (implementation complete and validated).

---

## Phase Summary

| Phase | Objective | Depends On | Can Parallel |
|---|---|---|---|
| A | Repository query | None | B, D |
| B | Multi-opportunity reconstruction | A | C |
| C | Contract change | B | E |
| D | OpportunityEngine lineage | A | C |
| E | Angular model | C | F, H, I |
| F | Scope selection UI | E | G |
| G | Per-market table | E, H | F |
| H | Opportunity chips | E | G |
| I | Objective labelling | E | F, G |
| J | Tests + validation | All | K |
| K | Story artifacts | J | — |

**Critical path:** A → B → C → E → F/G/H → J → K

**Parallel opportunities:**
- A ∥ D (repository query ∥ OpportunityEngine)
- F ∥ G ∥ H ∥ I (all frontend phases can be developed together after model update)
