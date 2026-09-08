# Repository Analysis — Story 0035

## Story

0035 — Scope Active Market Scans and Surface Per-Market Results

## Repository State

| Field | Value |
|---|---|
| Branch | `story/0035-scoped-active-scan-results` |
| HEAD | `ceac98e` |
| Working tree | Clean (code changes complete) |
| Story 0005 state | Merged — Active Scan scope resolution |
| Story 0022 state | Merged — Trigger market scan from product |
| Story 0025 state | Merged — Async active scan dispatch |
| ADR-033 status | Accepted |

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: trader chooses scan scope | Aligned — explicit scope selection (Specific/All eligible) |
| ADR-014 | Accepted | Market Intelligence produces observations, not trades | Aligned — scan results are informational only |
| ADR-020 | Accepted | Market Intelligence architecture | Aligned — Active Scan above AnalysisExecution |
| ADR-023 | Accepted | Capability Execution Model | Aligned — deterministic capabilities execute per strategy |
| ADR-025 | Accepted | Observation Model | Aligned — observations upstream of strategy evaluation |
| ADR-026 | Accepted | Trading Opportunity Model | Aligned — opportunities derived from StrategyMatch |
| ADR-033 | **Accepted** | Active and Passive Market Intelligence Orchestration | **Governing ADR** — Active Scanner architecture |
| ADR-034 | Accepted | Strategy, StrategyMatch and Trading Opportunity Boundaries | Aligned — StrategyMatch → TradingOpportunity lineage |

---

## Current Architecture

### Active Scan Flow (Before Story 0035)

```
Angular scan panel
    ↓
POST /api/v1/intelligence/scans (requestedMarketIds sent or omitted)
    ↓
ActiveScanService.create()
    → resolves candidate markets (full catalog if no requestedMarketIds)
    → resolves effective eligibility (MARKET_NOT_FOUND, MARKET_NOT_TRADABLE)
    → persists ActiveScan with scope snapshots
    ↓
ActiveScanService.start()
    → dispatches one AnalysisExecution per eligible market
    ↓
ProductionIntelligencePipeline per market
    → observations → capabilities → StrategyEvaluation → StrategyMatch → TradingOpportunity
    → PipelineRun finalized (opportunityId = single opportunity or null)
    ↓
ActiveScanReconciliationService.reconcileOwned()
    → loads PipelineRun per AnalysisExecution
    → fetches single TradingOpportunity by pipelineRun.opportunityId
    → projects MarketResult (singular opportunity)
    ↓
GET /api/v1/intelligence/scans/{scanId}
    → ActiveScanResponse with markets[].opportunity (singular)
```

**Gaps identified:**
1. Frontend never sends `requestedMarketIds` → invisible broad-scan default
2. `MarketResult.opportunity` is singular → zero and >1 opportunities lost
3. No per-market results UI → aggregate counters only
4. Objective text not labelled as provenance-only

### Opportunity Lineage (Write Path)

```
AnalysisExecution (per market)
    ↓
ProductionIntelligencePipeline evaluates ALL applicable strategies
    ↓
Each MATCH → StrategyMatch (persisted with analysisExecutionId, matchId)
    ↓
Each StrategyMatch → TradingOpportunity (persisted with strategyMatchId)
    ↓
PipelineRun finalized:
    - 0 opportunities → opportunityId = null
    - 1 opportunity  → opportunityId = that opportunity
    - N>1 opportunities → opportunityId = null (all persisted independently)
```

### Current ActiveScan Read Path (Broken for 0..N)

`ActiveScanReconciliationService.reconcileOwned()`:
1. Loads `PipelineRun` for each child `AnalysisExecution`
2. Collects `opportunityId` + `opportunityVersion` ONLY where non-null
3. Fetches via `TradingOpportunityRepository.findAllExact(refs)`
4. In `successWithOpportunity()`: looks up ONE opportunity via `pipelineRun.opportunityId()`

**Result:** When a market produces 0 or >1 opportunities, `PipelineRun.opportunityId` is null → no opportunities surfaced for that market.

### Market Catalogue Path (Existing)

- `GET /api/v1/markets` → Market Data Service `MarketController.findAll()`
- Gateway routes `/api/v1/markets/**` to market-data
- Angular `MarketService.findAll()` already calls this endpoint
- **No new backend endpoint, gateway route, or Angular service required**

---

## Existing Market Intelligence Components

### ActiveScanService

**Path:** `market-intelligence/src/main/java/.../application/scan/ActiveScanService.java`

Creates and starts active scans. Resolves scope (requested/candidate/effective markets). Dispatches `AnalysisExecution` per eligible market.

**Story 0035 change:** No change to scope resolution logic. Frontend now makes scope choice explicit.

### ActiveScanReconciliationService

**Path:** `market-intelligence/src/main/java/.../application/scan/ActiveScanReconciliationService.java`

Read-side reconciliation: loads PipelineRun per AnalysisExecution, fetches opportunities, projects MarketResult.

**Story 0035 change:** Replace single-opportunity lookup with multi-opportunity reconstruction via StrategyMatch lineage.

### ActiveScanResultProjection

**Path:** `market-intelligence/src/main/java/.../application/scan/ActiveScanResultProjection.java`

Projection interface defining `MarketResult` with `opportunity` field.

**Story 0035 change:** `opportunity` → `opportunities: List<TradingOpportunity>`.

### ActiveScanResponse

**Path:** `market-intelligence/src/main/java/.../adapter/web/ActiveScanResponse.java`

REST response DTO for scan results. `MarketResponse` with `opportunity` field.

**Story 0035 change:** `opportunity` → `opportunities: OpportunityResponse[]`.

### TradingOpportunityRepository

**Path:** `market-intelligence/src/main/java/.../application/port/TradingOpportunityRepository.java`

Port interface for TradingOpportunity persistence.

**Story 0035 change:** Add `findByStrategyMatchIds(Collection<UUID>)`.

### JpaTradingOpportunityRepository

**Path:** `market-intelligence/src/main/java/.../adapter/persistence/JpaTradingOpportunityRepository.java`

Spring Data JPA implementation.

**Story 0035 change:** Implement `findByStrategyMatchIds` via JPQL.

### OpportunityEngine

**Path:** `market-intelligence/src/main/java/.../application/opportunity/OpportunityEngine.java`

Creates TradingOpportunity from StrategyMatch.

**Story 0035 change:** Update to respect explicit `opportunityId` lineage when available.

---

## Angular /scan-panel Architecture (Existing)

### Component Structure

**Path:** `trading-os-web/src/app/features/opportunities/scan-panel/`

| File | Purpose |
|---|---|
| `scan-panel.ts` | Standalone component with reactive state |
| `scan-panel.html` | Template with account selector, trigger, results |
| `scan-panel.scss` | Dark theme styles |
| `scan-panel.spec.ts` | Component tests |

### Reactive Data Flow

```typescript
selectedAccountId$ → switchMap → scanService.getActiveScans()
    → combineLatest with timer(0, 10_000)
    → BehaviorSubject<ViewModel>
```

### Current Scan Panel UI

- Account selector (required)
- Optional objective text field
- "Run market scan" button
- Aggregate progress counters during scan
- Terminal state display

**Story 0035 adds:** Scope selection (Specific/All eligible), per-market results table, opportunity chips, objective provenance labelling.

---

## Gateway Analysis

### Current Routes

| Route | Path | Target |
|---|---|---|
| `intelligence` | `/api/v1/intelligence/**` | market-intelligence |
| `markets` | `/api/v1/markets/**` | market-data |

### Story 0035 Route Coverage

| Endpoint | Route Match | Change Required |
|---|---|---|
| `POST /api/v1/intelligence/scans` | `intelligence` | NO |
| `GET /api/v1/intelligence/scans/{scanId}` | `intelligence` | NO |
| `GET /api/v1/markets` | `markets` | NO |

```
GATEWAY_CHANGE_REQUIRED = NO
```

---

## Scope Semantics (Current Code)

- `requestedMarketIds`: user-provided explicit list (optional, nullable)
- `candidateMarketIds`: if requested null/empty → full catalog; else normalized requested
- `effectiveMarketIds`: eligible subset of candidates after existence + tradability checks
- Excluded markets persisted with `MarketEligibilityReason` (`MARKET_NOT_FOUND`, `MARKET_NOT_TRADABLE`)

### Broad-Scan Transport

Existing contract already supports both modes:
- Specific markets: `requestedMarketIds = [UUID...]`
- All eligible: `requestedMarketIds = null/empty` (backend resolves full catalog)

No new scope-mode domain concept needed. Frontend makes the choice explicit.

---

## Files to Modify (Summary)

| Module | File | Change |
|---|---|---|
| market-intelligence | `TradingOpportunityRepository.java` | Add `findByStrategyMatchIds` |
| market-intelligence | `JpaTradingOpportunityRepository.java` | Implement JPQL query |
| market-intelligence | `ActiveScanReconciliationService.java` | Multi-opportunity reconstruction |
| market-intelligence | `ActiveScanResultProjection.java` | `opportunities: List<TradingOpportunity>` |
| market-intelligence | `ActiveScanResponse.java` | `opportunities: OpportunityResponse[]` |
| market-intelligence | `OpportunityEngine.java` | Respect explicit opportunityId lineage |
| trading-os-web | `active-scan.model.ts` | `opportunities: OpportunityResponse[]` |
| trading-os-web | `scan-panel.ts` | Scope selection, filtering, navigation |
| trading-os-web | `scan-panel.html` | Per-market table, chips, routerLink |
| trading-os-web | `scan-panel.scss` | Table and chip styling |
| trading-os-web | `scan-panel.spec.ts` | Updated and new tests |

## Files to Create

None. All changes are modifications to existing files.

---

## Persistence

**No schema migration required.** The `strategyMatchId` column already exists on the `trading_opportunity` table from prior Story 0013/0022 work. All lineage for zero-to-many reconstruction already exists.
