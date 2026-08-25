# Story 0026 — Dashboard V1: Operational Account & Market Intelligence Overview

## Goal

Transform the current Trading OS Dashboard into a truthful operational overview of the trader's current state, combining Account data from Trading Core with Market Intelligence summary, while preserving zero/empty/unavailable/error semantics.

## Context

The investigation (`docs/architecture/reports/dashboard-functional-readiness.md`, commit `467371e`) established that:

- The Dashboard backend is a real-time aggregation engine (NOT a placeholder)
- Current `Equity = 0` is truthful (test account has €0.00 EUR, 0 trades, no risk rules)
- The Angular UI already renders most Account data correctly
- MI data (4 active opportunities, scan history) exists but is not surfaced
- Performance and MI panels are placeholders
- `catchError(() => of([]))` silently swallows account loading errors
- The architecture is CLIENT_COMPOSITION — Angular composes Trading Core + MI independently

## Problem

1. The Dashboard doesn't distinguish `0` (genuine) from unavailable data in the UI
2. MI panel is a placeholder — active opportunities and scan status are not visible
3. Account loading errors are silently swallowed (HTTP failure → "no accounts")
4. Risk UNAVAILABLE is shown without explanation
5. Performance panel is a placeholder with no useful content
6. Equity source ("CALCULATED" / "BROKER") is not displayed

## Scope

### In scope

- Fix accounts loading error semantics (loading / loaded / empty / error)
- Show equity source label ("Calculé" / "Broker")
- Explain risk UNAVAILABLE ("Aucune règle de risque configurée")
- Replace MI placeholder with real data (active opportunities count + last scan summary)
- Replace Performance placeholder with explicit "not available yet" state
- Error isolation: MI failure must not break Account Dashboard
- Tests for all new states

### Out of scope

- Equity history / time-series (requires backend snapshot service)
- Risk rules configuration UI
- Trade execution
- New analytics (Sharpe, VaR, win rate)
- Challenge-specific widgets
- Backend changes (Trading Core, Market Intelligence)
- Broker synchronization fixes (ZEUR/EUR, Trade.pnl, brokerEquity)
- News feed, economic calendar

## Acceptance criteria

1. Account loading shows distinct states: loading, loaded, empty, error
2. Equity displays source label when value is available
3. Risk UNAVAILABLE shows "Aucune règle de risque configurée"
4. MI panel shows active opportunities count and last scan status
5. MI panel links to /opportunities page
6. Performance panel shows explicit "not available yet" message
7. MI failure shows local error without breaking Account Dashboard
8. Account failure does not fabricate €0.00 values
9. All existing tests pass
10. New tests cover: account loading states, equity rendering, risk states, MI states, error isolation
11. Frontend build passes
12. No backend changes required

## Constraints

- CLIENT_COMPOSITION: Angular composes Trading Core + MI independently
- No new backend endpoints
- No new Angular services (reuse existing `OpportunityService`, `ActiveScanService`)
- Reactive architecture: Observable + async pipe + combineLatest
- Preserve existing dark dashboard design system
- UI language: French for status messages

## Relevant ADRs

- Investigation: `docs/architecture/reports/dashboard-functional-readiness.md`
- Story 0025: async active scan dispatch (MI APIs runtime-proven)
- Story 0022: trigger market scan from product (scan UI exists)

## Validation expectations

- `ng test` passes (existing + new)
- `ng build` succeeds
- Runtime: authenticated user → Dashboard loads → equity shown → MI loads → no cross-domain failures
