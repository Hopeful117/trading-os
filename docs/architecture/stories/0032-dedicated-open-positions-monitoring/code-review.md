# Code Review — Story 0032

## Review Scope

Story 0032 — Dedicated Open Positions Monitoring. Code review of all changes in working tree against Story 0032 acceptance criteria, ADR-001/014, and semantic invariants.

## Review Inputs

- Story: `docs/architecture/stories/0032-dedicated-open-positions-monitoring/story.md`
- Repository Analysis: `docs/architecture/stories/0032-dedicated-open-positions-monitoring/repository-analysis.md`
- Implementation Plan: `docs/architecture/stories/0032-dedicated-open-positions-monitoring/implementation-plan.md`
- ADRs: 001, 014
- Git diff: 3 modified files, 7 new files (excluding docs)

## Summary

Review identified 0 BLOCKER, 0 HIGH, 0 MEDIUM, 0 LOW findings. All acceptance criteria verified. Semantic invariants preserved.

## Findings

No findings. Review passed.

## Acceptance Criteria Verification

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC1 | Authenticated user can navigate to `/positions` | ✅ | Route registered in `app.routes.ts` with `authGuard` |
| AC2 | Page displays only authorized account positions | ✅ | `PositionController` uses `principal(authentication).getUserId()` + ownership check |
| AC3 | Each position shows all required fields | ✅ | `positions.html` template renders instrument, side, quantity, entry/current price, PnL, SL, TP, protection, openedAt |
| AC4 | PnL computed on backend | ✅ | `PositionQueryService` delegates to `PositionValuationService.value()` |
| AC5 | Protection status uses deterministic enum | ✅ | `PositionProtectionStatus` (PROTECTED, MISSING_STOP_LOSS, UNKNOWN) |
| AC6 | Deterministic alerts displayed | ✅ | Reused via `DashboardAlertService` in `PositionQueryService` |
| AC7 | Empty state when no positions | ✅ | Template: "Aucune position ouverte." when `positions.length === 0` |
| AC8 | Error state distinct from empty | ✅ | Template: error panel for `positionsError` with empty positions |
| AC9 | 10-second polling | ✅ | `timer(0, 10_000)` in `positions.ts` |
| AC10 | Polling stops on destroy | ✅ | `shareReplay({ bufferSize: 1, refCount: true })` + async pipe cleanup |
| AC11 | Sidebar link functional | ✅ | Sidebar already had `/positions` link; route now exists |
| AC12 | No broker mutation controls | ✅ | Template contains no buttons; test confirms `querySelector('button') === null` |
| AC13 | Tests pass, no regressions | ✅ | 262 + 257 = 519 tests, 0 failures |

## Semantic Invariant Verification

| Invariant | Status | Evidence |
|---|---|---|
| Read-only monitoring | ✅ | No broker-mutating controls in template. Test `no broker-mutating controls exist` confirms. |
| Broker-authoritative positions | ✅ | `PositionQueryService` reads from `BrokerPositionFact`, not from local state |
| Backend-computed PnL | ✅ | `PositionValuationService.value()` called in backend. Angular never calculates PnL. |
| No new Position aggregate | ✅ | No new entity/persistence. Positions remain live broker state. |
| No automatic execution | ✅ | Page is purely observational. No execution actions. |
| Cross-user protection | ✅ | Account-scoped endpoint with ownership verification |

## Code Quality

### Trading Core

- **PositionQueryService.java:** Clean extraction from DashboardQueryService. Shared logic now reusable. Logging consistent with existing patterns.
- **PositionController.java:** Minimal, focused endpoint. Follows existing controller conventions. Ownership pattern consistent with other controllers.
- **DashboardQueryService.java:** Refactored to delegate. Private methods removed. No behavior change.

### Angular

- **position.service.ts:** Simple HTTP client service. Follows existing service patterns.
- **positions.ts:** Reactive component with `combineLatest` + `switchMap`. Per-account cache for refresh failure preservation. No manual subscriptions.
- **positions.html:** Clean conditional chain handling all states (loading, error, warning, empty, positions). Warning banner for preserved-state display.
- **positions.scss:** Consistent with dashboard styling. Warning variant added for amber accent.

## Security Review

| Check | Status | Evidence |
|---|---|---|
| Authentication required | ✅ | `authGuard` on route; `principal(authentication)` in controller |
| Ownership verified | ✅ | Account ownership check in `PositionController` |
| No provider leakage | ✅ | `OpenPositionDashboardView` is broker-agnostic; no Kraken payloads exposed |
| No automatic execution | ✅ | Read-only page with no action buttons |
| No cross-user exposure | ✅ | Account-scoped endpoint |

## Test Coverage

| Module | Tests | New | Status |
|---|---|---|---|
| Trading Core | 262 | +4 (PositionController) | ✅ All pass |
| Angular | 257 | +15 (Positions component) | ✅ All pass |
| Angular Build | — | — | ✅ Production build succeeds |

## Recommendation

**APPROVED.** All acceptance criteria met. Semantic invariants preserved. No regressions. No security concerns.
