# Story 0032 — Implementation Report

## A. Story Reference

- **Story**: `docs/architecture/stories/0032-dedicated-open-positions-monitoring/story.md`
- **Status**: Completed
- **Branch**: `main`
- **Implementation Date**: 2026-09-03

## B. Summary

Implemented a read-only open positions monitoring page at `/positions`. The page exposes broker-authoritative position state through a dedicated endpoint and Angular component, with 10-second polling, per-account caching, and graceful degradation on refresh failure.

## C. Files Modified

### Trading Core (backend)

| File | Change |
|------|--------|
| `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/PositionQueryService.java` | **NEW** — Extracted shared position projection logic from `DashboardQueryService`. Reusable by both dashboard and new endpoint. |
| `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/DashboardQueryService.java` | Refactored to delegate to `PositionQueryService`. Removed private `buildPositions()`, `loadMarkets()`, `loadPrices()`, `normalize()`, `MarketLookup` inner class. |
| `trading-core/src/main/java/com/hope/trading/trading_core/controller/PositionController.java` | **NEW** — `GET /api/v1/accounts/{accountId}/positions` returning `List<OpenPositionDashboardView>`. |
| `trading-core/src/test/java/com/hope/trading/trading_core/controller/PositionControllerTest.java` | **NEW** — 4 tests: empty broker data, empty positions, position list, non-owner rejection. |
| `trading-core/src/test/java/com/hope/trading/trading_core/dashboard/service/DashboardQueryServiceTest.java` | Updated constructor to use `PositionQueryService`. Changed `findPriceSnapshots` verification from `times(1)` to `times(2)` due to double position query. |

### Angular (frontend)

| File | Change |
|------|--------|
| `trading-os-web/src/app/core/services/position.service.ts` | **NEW** — `getPositions(accountId)` calling the new endpoint through Gateway. |
| `trading-os-web/src/app/features/positions/pages/positions/positions.ts` | **NEW** — Standalone component with account selector, position cards, 10-second polling, per-account `lastPositionsByAccount` cache for refresh failure preservation. |
| `trading-os-web/src/app/features/positions/pages/positions/positions.html` | **NEW** — Template with loading, error, empty, warning (preserved state), and position card views. |
| `trading-os-web/src/app/features/positions/pages/positions/positions.scss` | **NEW** — Dashboard-oriented dark slate styling with `.warning` variant for preserved-state banner. |
| `trading-os-web/src/app/features/positions/pages/positions/positions.spec.ts` | **NEW** — 15 tests covering all states, account switching, polling, PnL classes, protection labels, no mutating controls. |
| `trading-os-web/src/app/app.routes.ts` | Registered `/positions` route with `authGuard`. |

## D. Architecture Decisions

### PositionQueryService extraction
Extracted from `DashboardQueryService.buildPositions()` to enable reuse by both the dashboard and the new position endpoint without duplicating position projection logic.

### Per-account last-positions cache
`Positions` component maintains a `Map<string, OpenPositionDashboardView[]>` rather than a flat array. This ensures that when switching accounts during a refresh failure, the correct account's last known positions are shown rather than a different account's data.

### Template conditional restructuring
The `@else if` chain was restructured so that `positionsError` with non-empty positions shows a warning banner above the position cards (preserved state), while `positionsError` with empty positions shows the full error state. This replaces the original design where error always hid positions.

### No new Position aggregate
Positions remain live broker state projected through `PositionQueryService`. No persistence layer changes. The `PositionController` delegates to the same `PositionQueryService` used by the dashboard.

## E. Validation

### Backend
- Trading Core tests: **262 tests passed, 0 failures** — BUILD SUCCESS

### Frontend
- Angular tests: **257 tests passed, 0 failures** (37 test files)
- Angular production build: **SUCCESS** (budget warning is pre-existing)

### Specific test coverage
- PositionController: 4 tests (empty broker data, empty positions, position list, non-owner rejection)
- DashboardQueryService: existing tests updated and passing
- Positions component: 15 tests (display, empty, loading, error, preserved state, Long/Short labels, protection status, PnL classes, account switching, polling, helper methods, no mutating controls)

## F. Known Limitations

- 10-second polling interval is hardcoded (no user configuration)
- No manual refresh button (polling only)
- No position detail drill-down view
- No sorting or filtering of positions
- The Angular bundle size budget warning (643 kB vs 500 kB budget) is pre-existing and unrelated to this story
