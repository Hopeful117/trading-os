# Story 0158 - Integrate the Market Navigator into the Trader Cockpit

## Metadata

**ID:** `0158`
**Title:** Integrate the Market Navigator into the Trader Cockpit
**Status:** Proposed

## Goal

Make the account-scoped Trader Cockpit the shortest path from the eligible market
universe to a market or Opportunity worth investigating, by reusing the existing
Active Scanner and synchronizing its result selection with the existing
DecisionWorkspace context.

## Context

Story 0157 established the persistent account and Risk context inside
`DecisionWorkspace`. The repository already contains an account-owned Active
Scanner frontend (`ScanPanel`), typed scan lifecycle/result contracts, backend
scope resolution, per-market result projection, and deterministic Opportunity
facts. Those capabilities currently live on the separate Opportunities route.

The next bounded product step is to bring that existing capability into the
Cockpit without creating a second scanner, a second market-stream owner, or a
new financial authority.

## Trader Question

> Which markets deserve my attention right now, and why?

## Scope

- Recompose the existing Active Scanner presentation into the account-scoped
  DecisionWorkspace as a compact Market Navigator region.
- Reuse `ActiveScanService`, its existing scan action, scope semantics, polling,
  idempotency behavior, and result projection.
- Make scan scope, scan lifecycle, result freshness, and backend status visible
  without inventing new status semantics or ranking.
- Present qualified, no-opportunity, excluded/unavailable, processing, and failed
  market results distinctly using the existing backend outcomes.
- Allow an eligible scan result to establish the existing active market context.
- Allow a scan Opportunity to establish the existing active market and
  Opportunity route context so the current investigation surface follows it.
- Preserve the Story 0157 account/Risk context while scans run, complete, or
  change account.
- Add focused frontend tests for scan lifecycle, result semantics, selection,
  account changes, deep links, and stream ownership behavior.

## Out of Scope

- Creating or redesigning the Active Scanner backend capability.
- New backend endpoints, DTOs, persistence, gateway routes, or domain changes.
- New ranking, scoring, confidence, "best trade", AI, or recommendation logic.
- Risk approval, Trade Plan acceptance, broker mutation, or execution behavior.
- New watchlist, portfolio management, position monitoring, news, or economic
  context functionality.
- Duplicating ticker, OHLC, Order Book, Recent Trades, or analysis streams.
- Replacing the existing Opportunity or Trade Plan workflows.
- Making Opportunity or Observation storage durable as part of this slice.

## Acceptance Criteria

- [ ] The Cockpit reuses the existing Active Scanner capability; no second scan
      orchestration path or scanner is created.
- [ ] With no account selected, the Navigator cannot start a scan and the
      account/Risk context remains truthful.
- [ ] The Cockpit exposes the existing scan action and enough scope information
      to understand what is being scanned.
- [ ] Scan lifecycle states remain truthful for submission, dispatch, running,
      partial completion, completion, failure, and no-work completion.
- [ ] Per-market results preserve the distinction between Opportunity found,
      completed without an Opportunity, excluded/unavailable, processing, and
      failed/expired outcomes.
- [ ] A completed scan with zero qualified Opportunities is a successful,
      explicit NO TRADE information state and does not appear as an error.
- [ ] Selecting an eligible result updates the existing active market context,
      route query parameters, and investigation streams through
      `DecisionWorkspace`.
- [ ] Selecting an Opportunity preserves the existing `opportunityId` and
      `marketId` deep-link semantics and renders the existing deterministic
      Opportunity evidence.
- [ ] Account changes invalidate prior scan/navigation context and cannot leave
      account A results visible with account B context.
- [ ] The Market Navigator does not open or own live market streams; only the
      existing DecisionWorkspace coordinator owns active-market streams.
- [ ] Account/Risk context from Story 0157 remains visible and account-scoped.
- [ ] Focused frontend tests cover no-account, running, completed, partial,
      failed, unavailable/excluded, zero-candidate, selection, deep-link, and
      account-switch behavior.
- [ ] The frontend test suite, production build, formatting, diff checks, and
      authenticated desktop/mobile runtime validation pass where available.
- [ ] No backend, Risk, execution, broker, schema, or unrelated frontend files
      change.

## Constraints

- Keep `DecisionWorkspace` as the context coordinator.
- Treat Active Scanner and Opportunity contracts as authoritative.
- Keep deterministic scanner facts distinct from human decision and future AI
  interpretation.
- Do not translate unavailable or failed analysis into rejection.
- Do not use Risk status to rank or filter scanner candidates.
- Keep human validation required before Trade Plan or execution workflows.
- Preserve existing Angular standalone, Observable, async-pipe, and cancellation
  conventions.
- Do not introduce new dependencies, global state, or an ADR without evidence.

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md`
- `docs/architecture/adr/ADR-014.md`
- `docs/architecture/adr/ADR-019.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-033.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-047.md`

## Relevant Stories

- `0006` - Persistent Active Scan Orchestration Foundation.
- `0021` - Surface active Market Intelligence opportunities in the Web app.
- `0025` - Async Active Scan Dispatch.
- `0137` - Finalize the Professional Decision Workspace.
- `0157` - Establish the Trader Cockpit Foundation.

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/features/opportunities/scan-panel`
- `trading-os-web/src/app/core/models/active-scan.model.ts`
- `trading-os-web/src/app/core/services/active-scan.service.ts`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`

## Validation

- Focused `DecisionWorkspace`, `ScanPanel`, service, and regression tests.
- Full Angular test suite.
- Angular production build.
- Prettier and `git diff --check`.
- Authenticated browser validation at desktop and narrow mobile widths where the
  local environment permits.
- Verify that scan/result HTTP traffic is account-scoped and that only the
  selected market owns live stream activity.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed.
- [ ] Independent Code Review approved.
- [ ] Human commit and integration completed.
