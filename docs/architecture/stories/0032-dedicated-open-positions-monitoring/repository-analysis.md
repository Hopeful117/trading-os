# Repository Analysis — Story 0032

## Story

0032 — Dedicated Open Positions Monitoring

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `3a5cfbe` |
| Working tree | Clean (untracked Story 0032 docs only) |

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: user is final decision maker | Aligned — read-only monitoring, no autonomous actions |
| ADR-014 | Accepted | Pipeline terminates with human validation | Aligned — positions are observation, not decisions |

## Current Position Architecture

### Broker Service Position Pipeline

```
Broker Service
  → PositionCapability.getPositions()
    → Broker API (Kraken)
      → List<PositionSnapshot>
        → Trading Core (via internal API)
```

### Dashboard Position Projection

```
DashboardQueryService
  → PositionQueryService.buildPositions()
    → BrokerPositionFact (broker position state)
    → MarketPriceFact (current price from Market Data)
    → PositionValuationService.value() (PnL computation)
    → DashboardAlertService (deterministic alerts)
    → PositionProtectionStatus (PROTECTED, MISSING_STOP_LOSS, UNKNOWN)
    → List<OpenPositionDashboardView>
```

### Current API Surface

| Endpoint | Module | Returns | Auth |
|---|---|---|---|
| `GET /api/v1/accounts/{accountId}/dashboard` | Trading Core | `DashboardSummary` (includes `openPositions`) | JWT + ownership |
| No dedicated positions endpoint | — | — | — |

**Assessment:** Position data is only available embedded in the full dashboard response. No focused positions API exists.

## Trading Core

### Existing Position-Related Classes

| Class | Responsibility | Reuse |
|---|---|---|
| `OpenPositionDashboardView` | Position projection record | ✅ Reuse as-is |
| `PositionValuationService` | PnL computation | ✅ Reuse as-is |
| `DashboardAlertService` | Deterministic position alerts | ✅ Reuse as-is |
| `PositionProtectionStatus` | Protection status enum | ✅ Reuse as-is |
| `PositionQueryService` | Shared position projection logic | ✅ Extract from DashboardQueryService |
| `DashboardQueryService` | Dashboard aggregation | Refactor to delegate to PositionQueryService |

### Account Ownership Pattern

All existing endpoints use `requireOwned()` or `principal(authentication).getUserId()` for authorization. The new positions endpoint must follow the same pattern.

## Gateway

### Current Routes

The existing Gateway routes do not include a `/positions` path. The Angular frontend routes through Gateway for all authenticated API calls.

### Required Gateway Change

`GET /api/v1/accounts/{accountId}/positions` needs a Gateway route. However, since the Gateway already routes `/api/v1/accounts/**` to Trading Core, the new endpoint should be covered by the existing wildcard.

## Angular

### Current Sidebar

The sidebar already contains a `/positions` link (line 11 of `sidebar.html`), but no route or component exists for it.

### Current Dashboard Position Display

The dashboard component displays positions in a table format within the full dashboard. This is not optimized for focused position monitoring.

### Missing

- `/positions` route
- `Positions` component
- `PositionService`

## Test Inventory

### Existing tests to preserve

| Test | Module | Status |
|---|---|---|
| DashboardQueryServiceTest | trading-core | Must update for PositionQueryService delegation |
| Dashboard spec tests | trading-os-web | No changes expected |
| PositionControllerTest | trading-core | Must create |

### New tests to create

| Test | Module | Purpose |
|---|---|---|
| PositionControllerTest | trading-core | Verify endpoint, ownership, empty/error states |
| Positions component spec | trading-os-web | Verify rendering, polling, empty/error states |

## Required Changes

1. Extract `PositionQueryService` from `DashboardQueryService`
2. Refactor `DashboardQueryService` to delegate to `PositionQueryService`
3. Create `PositionController` with `GET /api/v1/accounts/{accountId}/positions`
4. Create Angular `PositionService`
5. Create Angular `Positions` component with template and styles
6. Register `/positions` route with `authGuard`
7. Update template for error-with-preserved-positions display
8. Write backend tests
9. Write frontend tests

## Risks

1. **Template conditional chain:** Error state originally hid positions. Restructured to show warning banner when positions are preserved from cache. Verified by test.
2. **Timer-based polling in tests:** `BehaviorSubject.next()` with same value doesn't re-emit, requiring account switching to trigger re-fetch. Verified by test with two accounts.
3. **Bundle size budget:** Pre-existing budget warning (643 kB vs 500 kB). Unrelated to this story.

## Blockers

NONE. All evidence supports a clean implementation path.

## Recommendation

**READY_FOR_IMPLEMENTATION.** The backend has all required position projection logic. The gap is a focused endpoint and Angular page. No new domain concepts required.
