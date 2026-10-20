# Implementation Plan - Story 0134

## Status

Approved - implementation completed.

## Implementation Decision

Implement the approved **B - PARTIAL_IMPLEMENTATION** slice in `trading-os-web`
only. No backend contract change is planned.

The implementation will establish a compact context surface local to Decision
Workspace and preserve existing URL/query-parameter context. It will not create
a global store, a new business state machine, or a second decision surface.

## Files to Add

- `trading-os-web/src/app/features/decision-workspace/workspace-context/workspace-context.ts`
  - Standalone presentational component.
  - Typed inputs for account, selected market, Opportunity context, market
    freshness, and presentation workflow state.
  - No service injection, HTTP calls, subscriptions, eligibility calculation,
    Risk calculation, or execution command.
- `trading-os-web/src/app/features/decision-workspace/workspace-context/workspace-context.html`
  - Compact semantic context grid with primary account, market, tradability,
    and workflow values.
  - Secondary freshness, provider/mode-available values, and timestamps.
  - Explicit `UNKNOWN`/`UNAVAILABLE` rendering.
- `trading-os-web/src/app/features/decision-workspace/workspace-context/workspace-context.scss`
  - Existing dark-slate visual language, compact density, responsive narrow
    layout, visible focus, and non-color-only state treatment.
- `trading-os-web/src/app/features/decision-workspace/workspace-context/workspace-context.spec.ts`
  - Isolated presentation/state tests.

## Files to Modify

- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
  - Add the context component import.
  - Expose a view model for context presentation from existing account,
    `context$`, `marketView$`, `ticker$`, route Opportunity state, and current
    selection state.
  - Add only a small presentation-stage mapping based on existing states:
    account selection, market selection, Opportunity review, and market/trade
    preparation context. Do not introduce persisted lifecycle semantics.
  - Keep existing stream ownership and cleanup unchanged.
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
  - Render the context component once near the top of the workspace, before
    dense account/market evidence.
  - Pass existing projections without creating duplicate async subscriptions
    that trigger new HTTP or WebSocket work.
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`
  - Add only host integration spacing and any necessary responsive placement.
  - Avoid broad layout or panel restyling.
- `trading-os-web/src/app/features/markets/markets-details/markets-details.html`
  - Add a secondary `Review in Decision Workspace` link carrying the known
    `marketId` only. It must not add an account requirement to Market Detail.
- `trading-os-web/src/app/features/markets/markets-details/markets-details.ts`
  - Add Router navigation support only if the template requires a generated
    URL tree or query parameters; prefer a declarative RouterLink where
    possible.
- `trading-os-web/src/app/features/markets/markets-details/markets-details.spec.ts`
  - Verify the market-first link preserves `marketId` and does not require an
    account.
- `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts`
  - Add regression assertions for the existing Decision Workspace link's
    preserved Opportunity, market, and account query parameters.
- `docs/architecture/stories/0134-establish-account-scoped-trading-workspace-context/story.md`
  - Update status after implementation and validation.
- `docs/architecture/stories/0134-establish-account-scoped-trading-workspace-context/implementation-report.md`
  - Record implementation, documentation reconciliation, validation, and the
    Vault Outcome.

## Context Model

The component input model will remain presentation-only:

```text
WorkspaceContextView
  account: selected account projection or null
  market: loaded market projection or null
  opportunity: authoritative opportunity projection or null
  marketFreshness: LIVE | RECENT | STALE | UNAVAILABLE
  workflowStage: limited presentation label
  accountState: loading | selected | unavailable | error
  marketState: unselected | loading | ready | unavailable | error
```

The component will not accept or expose an Angular-computed `eligible` value as
authority. Eligibility remains represented by the existing Decision Context
selection path and market state remains represented by Market Data.

Account mode and challenge context will be rendered as unavailable/deferred
unless an existing authoritative field can be used without inference. The
implementation will not derive mode from account name, provider, brokerAccountId,
or route state.

## Workflow Presentation Mapping

The mapping will be deliberately small:

- `SELECT_ACCOUNT`: no account selected or account context is unresolved.
- `SELECT_MARKET`: account context is ready but no market is selected.
- `REVIEW_OPPORTUNITY`: authoritative Opportunity is present in the route
  context.
- `PREPARE_TRADE_PLAN`: selected market is ready and the manual/opportunity
  preparation actions are available.

The mapping is not persisted, sent to the backend, used for authorization, or
treated as a replacement for Trade Plan/Risk lifecycle state. If the component
cannot distinguish two stages without inference, it will use the broader safe
label rather than inventing a semantic.

## Route and Transition Strategy

- Keep the existing flat route table.
- Keep Decision Workspace query parameters `accountId`, `marketId`, and
  `opportunityId`.
- Add Market Detail -> Decision Workspace as a market-first secondary action
  carrying only `marketId`.
- Preserve the existing Opportunity Detail -> Decision Workspace link and its
  authoritative parameters.
- Do not add Dashboard links without a concrete existing workflow that carries
  enough context.
- Do not add Positions -> Decision Workspace navigation in this slice because
  the visible Position contract does not expose a canonical normalized market
  ID and Decision Workspace would imply investigation/preparation semantics.

## Stream and Performance Strategy

- Do not inject `MarketService`, `MarketDataStreamService`, or any HTTP service
  into the context component.
- Use the parent Decision Workspace projections.
- Keep the existing `selectedMarket$` stream ownership and subscription cleanup
  unchanged.
- Do not introduce a global context cache or eager loading.
- Validate that adding the context surface does not cause extra subscriptions
  to any market stream or repeated Decision Context resolution.

## Test Plan

### Context Component

- Renders selected account name and base currency.
- Renders market symbol/provider when loaded.
- Renders tradability and closure/unavailable state.
- Renders `UNKNOWN`/`UNAVAILABLE` when freshness is not authoritative.
- Renders each limited workflow presentation stage.
- Does not expose hidden account or provider identifiers as primary values.
- Retains keyboard-visible semantic structure.

### Decision Workspace

- Context is account-unselected before account selection.
- Account context appears after account-scoped resolution.
- Market context appears only after the existing eligible-market and market
  resolution path succeeds.
- Opportunity context remains preserved from query parameters.
- Market changes update context and retain existing stream cleanup behavior.
- Account changes clear the prior market/opportunity context as currently
  defined.
- No stream service calls are introduced by the context component.

### Market Detail

- Generic Market Detail remains usable without an account.
- The new link carries the normalized market ID to Decision Workspace.
- The link does not carry or invent an account ID.

### Opportunity Detail

- Existing Decision Workspace link preserves Opportunity and market identity.
- Existing account context remains preserved when available.
- Direct Trade Plan preparation and execution boundaries remain unchanged.

### Authority Regression

- No frontend test or presentation helper treats Opportunity score, Trend
  Context, or freshness as Risk approval.
- No new execution command or Execution Intent path is introduced.
- Market eligibility remains supplied by `DecisionContextService`.

## Validation Commands

From `trading-os-web`:

```text
npm run test:ci
npm run build
```

Also run the focused specs during iteration, formatting verification according
to repository conventions, and from the repository root:

```text
```

Runtime validation, when the local stack supports it:

- Market Detail -> Decision Workspace without account.
- Opportunity Detail -> Decision Workspace with Opportunity/market context.
- Decision Workspace account-first selection and eligible market selection.
- Account and market changes with stream cleanup inspection.
- Desktop and narrow viewport context hierarchy and keyboard focus.

## Documentation Reconciliation

No architectural ADR is expected. The Story status and implementation report
will record the partial implementation decision, deferred context dimensions,
and the absence of backend contract changes. The Professional Workspace UX
Investigation remains the governing UX artifact and will not be rewritten.

## Vault Outcome

No Obsidian vault action is proposed. The repository Story, ADRs, and current
source provide sufficient authoritative context; no curated personal note is
required for this implementation.

## Approval Gate

This Implementation Plan requires explicit human approval before production
code or tests are modified.
