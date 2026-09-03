# Engineering Report — Story 0032

## Story

0032 — Dedicated Open Positions Monitoring

**Date**: 2026-09-03
**Branch**: `main` (working tree changes, not yet committed)
**HEAD**: `3a5cfbe` → working tree changes

## Executive Summary

Story 0032 adds a dedicated open positions monitoring page at `/positions`. The trader can now inspect their currently open positions through a focused view that answers: "What positions do I currently have open, and what is their current deterministic state?"

Before this Story, position data was only available embedded in the full dashboard response (`DashboardSummary`). The sidebar had a `/positions` link but no route or page existed. The trader could not navigate to a focused view of their positions.

The implementation extracts shared position projection logic into `PositionQueryService`, adds a dedicated `GET /api/v1/accounts/{accountId}/positions` endpoint, and creates a reactive Angular component with 10-second polling, per-account caching, and graceful degradation on refresh failure.

**Key semantic invariant preserved:** This Story is strictly read-only position monitoring. No broker-mutating controls exist. Positions remain broker-authoritative external state.

---

## Original Problem

Three concrete issues prevented effective position monitoring:

1. **No Dedicated Positions Page:** The sidebar link `/positions` existed but had no corresponding route or component.
2. **Positions Embedded in Dashboard:** Position data was only available as part of `DashboardSummary`, coupling position monitoring to the full dashboard payload.
3. **No Dedicated Position Endpoint:** The dashboard endpoint returned account summary, risk, alerts, markets, and freshness — unnecessary for position monitoring.

---

## Architecture Before

```
DashboardQueryService:
  → buildPositions() (private)
    → BrokerPositionFact → MarketPriceFact → PositionValuationService
    → List<OpenPositionDashboardView> (embedded in DashboardSummary)

Angular Dashboard:
  → GET /api/v1/accounts/{accountId}/dashboard
    → Full DashboardSummary with openPositions embedded

No dedicated positions endpoint.
No /positions route.
Sidebar /positions link → 404.
```

## Architecture After

```
PositionQueryService (extracted):
  → buildPositions() (shared, reusable)
    → BrokerPositionFact → MarketPriceFact → PositionValuationService
    → List<OpenPositionDashboardView>

DashboardQueryService:
  → delegates to PositionQueryService

PositionController:
  → GET /api/v1/accounts/{accountId}/positions
    → PositionQueryService.buildPositions()
    → List<OpenPositionDashboardView>

Angular Positions:
  → GET /api/v1/accounts/{accountId}/positions (via PositionService)
  → 10-second polling via timer(0, 10_000) + switchMap
  → Per-account lastPositionsByAccount cache
  → Graceful degradation: warning banner + preserved positions on refresh failure
```

---

## Implementation Details

### Backend: PositionQueryService Extraction

Extracted from `DashboardQueryService` to enable reuse:

- `buildPositions(String accountId, Account account)` — main projection method
- `loadMarkets(List<String> marketIds)` — market catalogue lookup
- `loadPrices(List<String> marketIds)` — price snapshot lookup
- `normalize(String symbol)` — symbol normalization
- `MarketLookup` inner record — market catalogue cache

`DashboardQueryService` now delegates to `PositionQueryService`. No behavior change.

### Backend: PositionController

New endpoint `GET /api/v1/accounts/{accountId}/positions`:

- Authentication required via `principal(authentication).getUserId()`
- Account ownership verified
- Delegates to `PositionQueryService.buildPositions()`
- Returns `ResponseEntity<List<OpenPositionDashboardView>>`

### Frontend: PositionService

Simple HTTP client service:

- `getPositions(accountId)` → `GET /api/v1/accounts/{accountId}/positions`
- Returns `Observable<OpenPositionDashboardView[]>`

### Frontend: Positions Component

Standalone reactive component with:

- **Account selection:** `BehaviorSubject<string | null>` with auto-selection from accounts list
- **10-second polling:** `timer(0, 10_000)` + `switchMap` for automatic refresh
- **Per-account cache:** `Map<string, OpenPositionDashboardView[]>` preserves last known positions per account on refresh failure
- **Reactive view model:** `combineLatest` of accounts state and selected account, piped through `switchMap` for position fetching
- **No manual subscriptions:** Async pipe handles subscription lifecycle

### Frontend: Template States

| State | Condition | Display |
|---|---|---|
| Loading (initial) | `positionsLoading && positions.length === 0` | "Chargement des positions…" |
| Error (initial) | `positions.length === 0 && positionsError` | Error panel with message |
| Warning (refresh) | `positionsError` with `positions.length > 0` | Amber warning banner + position cards |
| Empty | `positions.length === 0` | "Aucune position ouverte." |
| Positions | `positions.length > 0` | Position cards with all fields |

---

## Semantic Decisions

### Read-Only Monitoring

`PositionController` is a read-only endpoint. The Angular component contains no buttons, no forms, no action handlers. The test `no broker-mutating controls exist` confirms this invariant.

### Broker-Authority Preserved

Positions remain live broker state projected through `BrokerPositionFact`. No new Position aggregate or persistence layer introduced. The `PositionQueryService` reads from the same source as the dashboard.

### Backend-Computed PnL

Unrealized PnL is computed by `PositionValuationService.value()` on the backend using `TradingCalculatorService.calculatePnL()`. Angular never calculates trading PnL. The `pnlClass()` method in the component is purely presentational (positive/negative CSS class).

### Per-Account Cache

The `lastPositionsByAccount` Map preserves positions per account ID. This ensures that when switching accounts during a refresh failure, the correct account's last known positions are shown rather than a different account's data.

### Warning vs Error

The template distinguishes between:
- **Error state** (initial failure, no positions): Full error panel
- **Warning state** (refresh failure, positions preserved): Amber banner above position cards

This provides better UX — the trader sees their last known positions with a freshness warning, rather than a blank error screen.

---

## Test Results

| Module | Command | Passed | Failed |
|---|---|---|---|
| Trading Core | `mvn test` | 262 | 0 |
| Angular | `npm run test:ci` | 257 | 0 |
| Angular Build | `npm run build` | OK | 0 |
| **Total** | | **519** | **0** |

---

## Files Changed

```
Trading Core (5 files):
  dashboard/service/PositionQueryService.java (NEW)
  dashboard/service/DashboardQueryService.java (modified)
  controller/PositionController.java (NEW)
  controller/PositionControllerTest.java (NEW — 4 tests including non-owner rejection)
  dashboard/service/DashboardQueryServiceTest.java (modified)

Angular (6 files):
  core/services/position.service.ts (NEW)
  features/positions/pages/positions/positions.ts (NEW)
  features/positions/pages/positions/positions.html (NEW)
  features/positions/pages/positions/positions.scss (NEW)
  features/positions/pages/positions/positions.spec.ts (NEW)
  app.routes.ts (modified)

Documentation (5 files):
  docs/.../0032-.../story.md
  docs/.../0032-.../implementation-report.md
  docs/.../0032-.../repository-analysis.md
  docs/.../0032-.../implementation-plan.md
  docs/.../0032-.../code-review.md
  docs/.../0032-.../engineering-report.md
```

---

## Remaining Gaps

Intentionally out of scope:

- Close position, partial close, modify SL/TP
- Position history, closed trades
- Execution→position correlation
- Multi-account aggregation
- WebSocket/SSE position streaming
- Real-time price updates
- Monitoring Agent, AI recommendations
- FTMO-specific rules
- Manual refresh button
- Position sorting/filtering
- Position detail drill-down

---

## Known Limitations

1. **10-second polling interval is hardcoded.** No user configuration for refresh rate. Future enhancement.
2. **No manual refresh button.** Polling is automatic only. Future enhancement.
3. **Bundle size budget warning.** Pre-existing (643 kB vs 500 kB budget). Unrelated to this Story.
4. **No position sorting or filtering.** All positions displayed in default order. Future enhancement.

---

## Recommendation

Story 0032 is **COMPLETE**. All acceptance criteria met. Semantic invariants preserved. 519 tests pass. No regressions. No security concerns. Changes ready for human review and commit.
