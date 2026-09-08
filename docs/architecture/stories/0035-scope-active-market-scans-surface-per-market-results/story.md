# Story 0035 — Scope Active Market Scans and Surface Per-Market Results

## Metadata

**ID:** `0035`

**Title:** Scope Active Market Scans and Surface Per-Market Results

**Status:** Draft

---

## Goal

Enable a trader to explicitly choose the scope of an Active Scan (specific markets or explicit all-eligible), submit the request, and inspect truthful per-market results — including zero, one, or multiple opportunities per market — with navigation to the existing opportunity detail.

```text
Trader
  ↓
chooses Active Scan scope explicitly
  ↓
specific markets OR explicit all-eligible
  ↓
Market Intelligence resolves authoritative scope
  ↓
scan executes asynchronously
  ↓
trader sees truthful per-market outcomes
  ↓
0..N opportunities remain traceable per market
  ↓
trader can open existing opportunity detail
```

---

## Context

ADR-033 establishes the Active Scanner as intention-driven orchestration above single-market `AnalysisExecution`. The current implementation (Stories 0005–0007, 0022, 0025) provides:

- Persisted `ActiveScan` with requested/candidate/effective scope snapshots
- Deterministic eligibility resolution before expensive analysis
- One `AnalysisExecution(ACTIVE)` per eligible market
- Asynchronous dispatch with idempotent creation
- Read-side lifecycle reconciliation (`RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`, `COMPLETED_NO_WORK`)
- Per-market result projection with exclusion diagnostics and analysis status
- Frontend scan panel (account selection, optional objective, trigger, polling, terminal states)

**Remaining gaps:**

1. **No explicit market selection** — the frontend never sends `requestedMarketIds`; broad scanning is an invisible default when the list is omitted/empty
2. **Singular opportunity assumption** — `MarketResult.opportunity` is a single `TradingOpportunity | null`; ADR-033 §Per-Market Result and Opportunity Cardinality requires zero-to-many traceability
3. **No per-market results UI** — the scan panel only shows aggregate progress counters; traders cannot inspect individual market outcomes or navigate to opportunities
4. **Objective provenance clarity** — the optional objective text is persisted but the UI does not label it as provenance-only

Story 0035 closes these gaps with the minimum vertical slice.

---

## Problem

A trader using Trading OS Web today can trigger an Active Scan, but:

- They cannot choose **which** markets to scan; the backend silently scans the full eligible catalogue
- They cannot distinguish **explicit all-eligible** from **implicit default**
- They see only aggregate counters while the scan runs and at completion
- If a market produces multiple opportunities, only one (or none) is surfaced
- There is no navigation from scan results to the existing Opportunities detail

This prevents the trader from validating scan coverage, diagnosing exclusions, or reaching the opportunities that matter.

---

## Scope

### Included

**Backend (market-intelligence)**

- Remediate the singular-opportunity projection to preserve zero-to-many cardinality per market
- Ensure `ActiveScanResultProjection.MarketResult` exposes all authoritative opportunities for the child analysis (via existing `StrategyMatch` → `TradingOpportunity` lineage)
- Add `TradingOpportunityRepository.findByStrategyMatchIds(Collection<UUID>)` query
- No persistence migration required — current lineage already allows reconstruction
- API contract change: `MarketResponse.opportunity` → `opportunities: OpportunityResponse[]`
- Preserve existing idempotency, dispatch, reconciliation, and lifecycle semantics
- Scope resolution (Story 0005) remains authoritative for eligibility

**API**

- `POST /api/v1/intelligence/scans` request body unchanged (`requestedMarketIds` already exists)
- `GET /api/v1/intelligence/scans/{scanId}` response: `markets[].opportunity` → `markets[].opportunities[]`
- Each opportunity reference contains minimum fields for summary + navigation: `id`, `instrument`, `direction`, `scenario`, `timeframe`, `status`, `score`, `evaluatedAt`, `strategyMatchId`

### API Contract Change

The response contract change from singular `opportunity: OpportunityResponse | null` to array `opportunities: OpportunityResponse[]` is a **breaking change**. It is acceptable for pre-V1 because Trading OS controls both the backend producer and Angular consumer, and Story 0035 migrates both sides together before a stable public V1 compatibility guarantee exists. No API versioning, dual-field compatibility, or deprecation infrastructure is introduced. The migration boundary is atomic:

```text
Backend projection
        ↓
REST response contract
        ↓
Angular model
        ↓
Angular consumer

migrated together in Story 0035
```

**Frontend (trading-os-web)**

- Reuse existing `MarketService.findAll()` for catalogue loading (`GET /api/v1/markets` via Gateway)
- Explicit scope selection: **Specific markets** (multi-select from catalogue) vs **All eligible markets** (explicit choice)
- `requestedMarketIds` sent only for specific selection; omitted for all-eligible
- Per-market results table: compact, filterable, scannable
- Filters: All | Opportunity produced | Completed no opportunity | Excluded | Failed | Still processing
- Each row shows: market identity, eligibility, exclusion reason, analysis lifecycle, diagnostic, zero-to-many opportunity chips/links
- Click opportunity → navigate to existing `/opportunities/{id}` detail route
- Objective field labelled as "Provenance / context (does not influence scope, eligibility, or ranking)"
- Preserve existing polling, error handling, double-trigger protection

### Out of Scope

- Passive Scanner (any aspect)
- Saved watchlists (selection is ad hoc only)
- Scheduled/background scanning
- AI Engine / LLM integration / RAG / agents
- News Service / economic calendar
- Ranking / global score calibration / "best trade" claims
- Strategy validation / new deterministic strategies
- Scan cancellation / automatic restart recovery
- Durable dispatch queue / message broker
- Execution / Risk changes / Broker changes / Kraken E2E / new broker provider
- Position monitoring / trading journal / trading analytics
- `OpportunityOrigin.ACTIVE_SCAN` vs `USER_REQUEST` debt (leave unchanged unless it blocks correctness)

---

## Acceptance Criteria

- [ ] **AC01** Trader can explicitly choose specific markets from the catalogue
- [ ] **AC02** Trader can explicitly choose all eligible markets (distinct UI action)
- [ ] **AC03** Broad scanning is no longer an invisible frontend default
- [ ] **AC04** Requested scope is sent to the existing authoritative backend flow
- [ ] **AC05** Effective eligibility remains backend-authoritative (frontend never derives/overrides)
- [ ] **AC06** Per-market outcomes are visible in a compact filterable table
- [ ] **AC07** Excluded and failed markets expose truthful backend reasons
- [ ] **AC08** Successful siblings remain visible when another market fails
- [ ] **AC09** One market may expose zero opportunities
- [ ] **AC10** One market may expose one opportunity
- [ ] **AC11** One market may expose multiple opportunities
- [ ] **AC12** No arbitrary opportunity is selected or discarded
- [ ] **AC13** Every surfaced opportunity navigates to the existing Opportunity detail
- [ ] **AC14** Objective text is represented as provenance/context only
- [ ] **AC15** No global ranking claim is introduced
- [ ] **AC16** Existing asynchronous scan/polling behavior remains valid
- [ ] **AC17** Existing ActiveScan idempotency semantics remain valid
- [ ] **AC18** Backend and Angular automated tests cover the new behavior
- [ ] **AC19** Production builds/checks required by repository governance pass

---

## Constraints

- Preserve ADR-033 Active/Passive orchestration semantics
- `ActiveScan` remains above `AnalysisExecution`; `AnalysisExecution` remains single-market
- `PipelineRun` remains scoped to one `AnalysisExecution`
- Market Intelligence remains authoritative for eligibility and effective scope
- Risk Domain remains authoritative for final trade-risk validation
- Human trader retains final authority over trade decisions
- Do not introduce SSE/WebSocket/new realtime infrastructure
- Reuse existing Angular reactive patterns (Observables, async pipe)
- Keep visual style: dark slate, dashboard-oriented, clean, subtle blue accents

---

## Relevant ADRs

- `ADR-033` — Active and Passive Market Intelligence Orchestration (Accepted 2026-09-07)
- `ADR-034` — Strategy, StrategyMatch and Trading Opportunity Boundaries
- `ADR-020` — Market Intelligence Architecture
- `ADR-023` — Capability Execution Model
- `ADR-025` — Observation Model
- `ADR-026` — Trading Opportunity Model
- `ADR-028` — Risk Engine Architecture
- `ADR-030` — Broker Service Architecture

---

## Relevant Modules

- `market-intelligence` (domain, application, adapter/web, adapter/persistence)
- `trading-os-web` (core/models, core/services, features/opportunities/scan-panel)

---

## Technical Investigation Summary

### Opportunity Lineage (Write Path)

```text
AnalysisExecution (per market)
    ↓
ProductionIntelligencePipeline evaluates ALL applicable strategies
    ↓
Each MATCH → StrategyMatch (persisted with analysisExecutionId, matchId)
    ↓
Each StrategyMatch → TradingOpportunity (persisted with strategyMatchId)
    ↓
PipelineRun completed:
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

### Remediation (Zero-to-Many)

**Authoritative reconstruction path:**
```
ActiveScanMarket.analysisExecutionId
    → StrategyMatchRepository.findByAnalysisExecutionId() → List<StrategyMatch>
    → extract matchIds
    → TradingOpportunityRepository.findByStrategyMatchIds(matchIds) → List<TradingOpportunity>
```

**Changes required:**
- `TradingOpportunityRepository`: add `findByStrategyMatchIds(Collection<UUID>)`
- `JpaTradingOpportunityRepository`: implement via Spring Data query on `strategyMatchId`
- `ActiveScanReconciliationService`: replace single-opportunity lookup with multi-opportunity reconstruction per market
- `ActiveScanResultProjection.MarketResult`: `opportunity` → `opportunities: List<TradingOpportunity>`
- `ActiveScanResponse.MarketResponse`: `opportunity` → `opportunities: OpportunityResponse[]`

**No persistence/migration changes** — all lineage already exists.

### Opportunity Ordering

No authoritative domain ordering exists for multiple opportunities per market. `StrategyMatch.matchedAt` and `TradingOpportunity.evaluatedAt` provide timestamps but no business rule mandates ordering. Story 0035 does not impose any ordering semantics. Multiple opportunities produced for one market have no authoritative business ranking or ordering. The implementation must preserve all authoritative opportunities. Presentation order MUST NOT be interpreted as best, recommended, highest priority, highest quality, or ranked. If implementation later requires technically stable serialization for deterministic UI rendering, that is an implementation concern and must remain semantically neutral unless existing architecture establishes otherwise.

### Scope Semantics (Current Code)

- `requestedMarketIds`: user-provided explicit list (optional, nullable)
- `candidateMarketIds`: if requested null/empty → full catalog; else normalized requested
- `effectiveMarketIds`: eligible subset of candidates after existence + tradability checks
- Excluded markets persisted with `MarketEligibilityReason` (`MARKET_NOT_FOUND`, `MARKET_NOT_TRADABLE`)

### Broad-Scan Transport

Existing contract already supports both modes:
- Specific markets: `requestedMarketIds = [UUID...]`
- All eligible: `requestedMarketIds = null/empty` (backend resolves full catalog)

No new scope-mode domain concept needed. Frontend makes the choice explicit.

### Market Catalogue Path (Resolved)

- `GET /api/v1/markets` → Market Data Service `MarketController.findAll()`
- Gateway routes `/api/v1/markets/**` to market-data
- Angular `MarketService.findAll()` already calls this endpoint
- **No new backend endpoint, gateway route, or Angular service required**

---

## Behavioral Scenarios (Given/When/Then)

**S01 — Specific single-market scan**
- Given trader on Opportunities page with accounts loaded
- When they select "Specific markets", choose one market, optionally enter objective, click "Run market scan"
- Then scan created with `requestedMarketIds` containing that market; backend resolves eligibility for that market only; per-market results shows one row with true outcome; opportunities (if any) appear as navigable chips

**S02 — Specific multi-market scan**
- Given trader on Opportunities page
- When they select "Specific markets", choose multiple markets, run scan
- Then `requestedMarketIds` contains all selected IDs; backend resolves eligibility per market; results table shows one row per requested market with individual outcomes

**S03 — Explicit all-eligible scan**
- Given trader on Opportunities page
- When they select "All eligible markets" (explicit radio/button) and run scan
- Then `requestedMarketIds` omitted; backend resolves full catalog; results show all eligible markets; UI clearly indicates explicit all-eligible choice

**S04 — Requested market excluded by backend**
- Given scan run with specific markets including one non-tradable
- When scan completes
- Then excluded market appears with `eligible=false`, `exclusionReasons=[MARKET_NOT_TRADABLE]`, `outcome=EXCLUDED`, no `analysisExecutionId`; other markets unaffected

**S05 — Partial scan completion**
- Given multi-market scan running
- When some children complete and others still running
- Then scan status=`RUNNING`; completed rows show final outcomes; running rows show `analysisStatus=RUNNING`; filter "Still processing" shows only running rows; polling continues until all resolve

**S06 — Completed scan with zero opportunities**
- Given scan runs against eligible markets
- When all children complete with `COMPLETED_NO_SIGNAL`
- Then scan status=`COMPLETED`, `opportunitiesFound=0`; each market row `outcome=COMPLETED_NO_OPPORTUNITY`, `opportunities=[]`; UI shows "No setup matched" hint (not error)

**S07 — One opportunity**
- Given scan runs, one market produces exactly one opportunity
- When scan reaches terminal status
- Then market row `outcome=OPPORTUNITY_FOUND`, `opportunities` array has one element; chip shows instrument/direction/scenario/score; click navigates to `/opportunities/{id}`

**S08 — Multiple opportunities for one market**
- Given scan runs, one market produces multiple StrategyMatch → multiple TradingOpportunity
- When scan reaches terminal status
- Then market row `outcome=OPPORTUNITY_FOUND`, `opportunities` array contains all (≥2); each appears as separate chip/link; no opportunity hidden; each navigates to its detail

**S09 — Opportunities across multiple markets**
- Given scan runs against multiple eligible markets
- When several markets each produce opportunities
- Then results show all markets with respective opportunity counts; `progress.opportunitiesFound` = total across markets; filter "Opportunity produced" shows only markets with ≥1; each opportunity independently navigable

**S10 — Failed child analysis**
- Given multi-market scan where one child fails
- When scan reaches terminal status
- Then scan status=`PARTIALLY_COMPLETED` (if other successes) or `FAILED` (if all fail); failed row shows `outcome=FAILED` with diagnostic; successful siblings remain visible with results; no successful result lost

**S11 — Objective supplied**
- Given trader enters objective text before running scan
- When scan created
- Then objective persisted on `ActiveScan.objective`, appears in response; UI labels field as provenance-only; backend does not use for filtering/eligibility/ranking/strategy selection

**S12 — Objective omitted**
- Given trader leaves objective empty
- When scan created
- Then `objective=""` (normalized); scan proceeds normally; no provenance text stored

**S13 — Market catalogue unavailable**
- Given trader opens scan panel
- When catalogue API fails
- Then UI shows inline error "Unable to load markets" with Retry button
- And "Specific markets" selection is unavailable (cannot choose markets without catalogue)
- And "All eligible markets" remains available and functional (does not require frontend catalogue)
- And "Run market scan" is enabled only when a valid scope choice is available (All eligible) or when catalogue loads and Specific markets is selected

**S14 — Opportunity navigation**
- Given completed scan with opportunities in results
- When trader clicks opportunity chip/link
- Then router navigates to `/opportunities/{opportunityId}`; existing Opportunity detail loads; no duplicate rendering in scan panel

**S15 — Market eligibility changes between selection and resolution**
- Given trader selects specific markets including one currently tradable
- When scan submitted and backend resolves scope
- And that market became non-tradable by resolution time
- Then backend excludes it with `MARKET_NOT_TRADABLE`; results show it as excluded with reason; frontend does not override; backend authority preserved

---

## Definition of Done

- [ ] ADR-033 accepted per repository workflow
- [ ] Story 0035 `story.md` created and reviewed
- [ ] `ActiveScanResultProjection.MarketResult` exposes `opportunities: List<TradingOpportunity>`
- [ ] `TradingOpportunityRepository.findByStrategyMatchIds(Collection<UUID>)` implemented
- [ ] `ActiveScanReconciliationService` reconstructs all opportunities per market via StrategyMatch lineage
- [ ] `ActiveScanResponse.MarketResponse` exposes `opportunities: OpportunityResponse[]`
- [ ] Angular `ActiveScanMarketResult` uses `opportunities: OpportunityResponse[]`
- [ ] Angular scope selection: Specific markets (multi-select) + All eligible markets (explicit choice)
- [ ] Per-market results table with filters (All | Opportunity produced | Completed no opportunity | Excluded | Failed | Still processing)
- [ ] Excluded markets show backend exclusion reasons; failed markets show diagnostics
- [ ] Zero-to-many opportunity chips with navigation to existing `/opportunities/{id}`
- [ ] Objective field labelled as provenance-only
- [ ] Automated tests pass (backend reconciliation + frontend scope/results/navigation)
- [ ] Angular production build succeeds
- [ ] `git diff --check` passes
- [ ] No out-of-scope functionality introduced

---

## Validation

- Targeted `market-intelligence` tests for projection cardinality, API contract, reconciliation
- Targeted `trading-os-web` tests for scope selection, per-market rendering, filtering, navigation
- Full `market-intelligence` Maven test suite
- Angular `npm run test:ci` and `npm run build`
- `git diff --check`
- Manual review of the diff before approval

---

## Story Lifecycle

| Status | Value |
|--------|-------|
| IMPLEMENTATION_COMPLETE | YES |
| FINAL_SAFETY_REVIEW_COMPLETE | YES |
| HUMAN_ACCEPTANCE | YES |
| STORY_0035_ACCEPTED | YES |