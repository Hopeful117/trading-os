# Story 0026 — Implementation Report

**Date**: 2026-08-25
**Branch**: `feature/story-0026-dashboard-operational-overview`
**Status**: DONE

---

## Changes

### Files Modified

| File | Change |
|------|--------|
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.ts` | Rewritten: `AccountsState`/`MiState` interfaces, reactive `accountsState$` + `miState$` + combined `viewModel$`, `equitySourceLabel()` method |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.html` | Rewritten: account loading/error/empty states, equity source label, risk UNAVAILABLE explanation, MI panel with active opportunities, Performance panel explicit message |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.scss` | Updated: `.mi-panel`, `.mi-summary`, `.mi-stat`, `.panel-link`, `.empty-state.error` styles |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.spec.ts` | Rewritten: 27 tests covering all states, error isolation, equity source, risk, MI panel |

### No Files Created or Deleted

---

## Implementation Details

### AccountsState + MiState reactive architecture

- `accountsState$`: emits `{status, accounts?, selectedAccountId?, error?}` — loading → loaded | error
- `miState$`: emits `{status, opportunities?, error?}` — loading → loaded | error
- Combined `viewModel$`: `combineLatest([accountsState$, dashboardState$, miState$])` with shared ErrorBoundary
- MI panel **outside** account dashboard conditional block — renders independently, survives account errors

### Equity source label

- `equitySourceLabel()` method returns human-readable French labels:
  - `BROKER` → "Provenance : broker"
  - `CALCULATED` → "calculé à partir du compte broker"
  - `UNKNOWN` → "UNKNOWN"

### Risk UNAVAILABLE explanation

- When `risk.status === 'UNAVAILABLE'`: shows "Aucune règle de risque configurée pour ce compte."
- When rules exist: shows risk summary (daily loss %, drawdown %) and individual rules

### MI panel

- Shows active opportunity count via `OpportunityService.findActive()` → `GET /api/v1/opportunities/active`
- Link to `/opportunities` page
- Loading and error states handled independently
- Error message: "Market Intelligence temporairement indisponible."

### Performance panel

- Explicit "not available yet" message: "L'historique d'equity n'est pas encore disponible."
- No fabricated data

### Error isolation

- MI failure does not break account dashboard (tested)
- Account dashboard failure does not fabricate MI values (tested)

---

## Tests

- **27 tests** in dashboard.spec.ts (up from 8)
- **227 total frontend tests** (up from 216)
- All tests pass (`ng test --watch=false`)
- Build succeeds (`ng build`)
- `git diff --check` clean

---

## Runtime Validation

- Docker image rebuilt and deployed
- Dashboard API: equity=0, risk=UNAVAILABLE, equitySource=CALCULATED
- MI API: 0 active opportunities (empty array)
- Angular frontend served correctly with new build

---

## Decisions

- **CLIENT_COMPOSITION**: Angular composes Trading Core Dashboard API + MI APIs independently in parallel. No BFF, no backend aggregator
- **MI panel outside account conditional**: Ensures MI data is visible even when accounts fail to load
- **No new Angular services**: Reused existing `OpportunityService`, `AccountService`, `DashboardService`
- **No backend changes**: All work in `trading-os-web`
