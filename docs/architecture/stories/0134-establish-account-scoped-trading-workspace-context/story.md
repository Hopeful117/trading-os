# Story 0134 - Establish the Account-Scoped Trading Workspace Context

## Status

Runtime validation completed - pending human commit.

## Goal

Establish a small authoritative context surface around the existing account-
scoped Decision Workspace so a trader can immediately identify the active
account, market or opportunity, relevant freshness, workflow stage, and next
safe action.

This Story is an incremental workspace-context slice. It is not a terminal
redesign, Dashboard replacement, global state store, or new backend service.

## Product Principle

Trading OS supports Awareness -> Investigation -> Decision -> Execution ->
Monitoring -> Review while preserving deterministic Risk, human validation,
explicit execution authorization, service ownership, market eligibility, and
security boundaries.

## Scope

- Add the smallest reusable compact context header/rail justified by the
  Decision Workspace layout.
- Display authoritative selected account identity and available provider/mode
  context without duplicating full account details.
- Display authoritative selected market identity and tradability state.
- Display distinct freshness/status dimensions only where existing contracts
  provide them; unknown remains unknown.
- Derive a small presentation workflow stage from existing route/context and
  lifecycle state without creating a second Angular business state machine.
- Preserve market-first, opportunity-first, account-first, and position-first
  entry semantics.
- Preserve market exploration, Research, News, and Economic Context without
  making them account-dependent.
- Preserve URL-addressable market and opportunity context where existing route
  conventions support it.
- Preserve the existing Decision Workspace as the canonical decision surface.
- Add context-preserving transitions from supported existing entry points only
  where the transition is semantically valid.
- Add focused Angular tests and runtime validation for the implemented slice.

## Out of Scope

- Bloomberg-style terminal replacement.
- Dashboard redesign or replacement.
- Full Decision Workspace density/layout redesign.
- New backend orchestration service or broad backend state model.
- NgRx, generic global frontend state, or eager cross-feature caching.
- Global command palette, keyboard shortcut system, detachable windows, or
  user-configurable layouts.
- Research Experiment Detail, Research Results, or attaching Research integrity
  to the active trading context.
- Broad News or Economic Context integration.
- AI chatbot or AI execution controls.
- Automatic execution or Risk semantic changes.
- Full mobile redesign or complete Design System creation.
- Unrelated refactoring.

## Entry Workflow Constraints

The account-scoped workspace is account-explicit only once the user enters a
tradable workflow requiring account-specific eligibility, planning, Risk, or
execution semantics.

- Market-first: Market -> investigation -> decision context -> account when
  required -> Trade Plan.
- Opportunity-first: Opportunity -> evidence review -> Decision Workspace ->
  account when required -> Trade Plan.
- Account-first: Account -> eligible markets -> Decision Workspace -> Trade
  Plan.
- Position-first: Position -> account and market context -> monitoring or
  management.

Generic Markets, Market Detail, Research, News, and Economic Context must not
require a selected account merely because workspace context exists.

## Authority Boundaries

- Trading Core owns account ownership, Trade Plan orchestration, and execution
  state.
- Market Data owns market identity, state, live facts, and market freshness.
- Market Intelligence owns Opportunity identity, provenance, and analytical
  evidence.
- Risk owns deterministic Risk decisions.
- Broker Service owns broker-specific connectivity and outcomes.
- Angular owns presentation, navigation, and explicit user intent only.

Angular must not calculate account eligibility, market eligibility, Risk
approval, execution authorization, Opportunity validity, or Trade Plan validity.
Unknown or unavailable authoritative values must remain visibly unknown or
unavailable.

## Acceptance Criteria

- [ ] Decision Workspace exposes a compact context surface without becoming a
      second trading-decision implementation.
- [ ] The surface distinguishes account, market, tradability, workflow stage,
      and freshness by visual hierarchy.
- [ ] Account context uses authoritative account data and preserves loading,
      empty, unavailable, and error semantics.
- [ ] Market context uses normalized identity and authoritative market state;
      Angular does not calculate eligibility.
- [ ] Freshness is shown only for authoritative existing projections and is not
      collapsed into an invented generic LIVE flag.
- [ ] Workflow stage is a presentation projection of existing route/context and
      lifecycle semantics, not a new business state machine.
- [ ] Market-first exploration remains possible without prior account
      selection.
- [ ] Opportunity-first navigation preserves opportunity and market context
      where authoritative.
- [ ] Account-first navigation continues to resolve eligible markets through the
      existing Decision Context contract.
- [ ] Position-first behavior is either implemented only where semantically
      valid or explicitly documented as deferred.
- [ ] Existing Sidebar and Navbar responsibilities remain unchanged except for
      necessary context navigation integration.
- [ ] Research remains separate and preserves Integrity != Readiness != Results.
- [ ] No backend changes are made unless a required authoritative field is
      demonstrably missing; any such change remains additive and owned by the
      correct service.
- [ ] Context header/rail does not create duplicate market streams or eager
      cross-feature data loading.
- [ ] Focused Angular tests cover context states, route-derived context, account
      and market changes, cleanup, and supported entry transitions.
- [ ] Authority-boundary regression tests prove Angular does not calculate Risk,
      eligibility, or execution authorization.
- [ ] Angular CI tests, production build, formatting checks, and `git diff
      --check` pass.
- [ ] Runtime walkthrough covers market-first, opportunity-first, account-first,
      and the position-first decision.
- [ ] Desktop and narrow viewport validation confirms readable compact context,
      visible state hierarchy, keyboard focus, and no excessive vertical growth.
- [ ] Protected and unrelated files remain unchanged.

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md` - Trading OS vision and human authority.
- `docs/architecture/adr/ADR-014.md` - Deterministic trading decision pipeline.
- `docs/architecture/adr/ADR-031.md` - Planning context versus Risk context.
- `docs/architecture/adr/ADR-043.md` - Account and BrokerAccount identity.
- `docs/architecture/adr/ADR-047.md` - Manual Trade Plans as a first-class
  origin.

## Relevant Stories

- `0132` - Sidebar and application navigation.
- `0133` - Research integrity observability.
- `0062` - Opportunity and Decision Workspace convergence.
- `0082` - Opportunity-to-TradePlan and Risk transition.
- `0026` - Dashboard operational overview.

## Relevant Modules

- `trading-os-web/src/app/layout`
- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/features/markets`
- `trading-os-web/src/app/features/opportunities`
- `trading-os-web/src/app/features/dashboard`
- `trading-os-web/src/app/features/positions`
- `trading-os-web/src/app/core/services`
- `trading-os-web/src/app/core/models`

## Validation

- Focused Angular context, Decision Workspace, routing, and affected entry-point
  tests.
- Complete Angular `npm run test:ci` and `npm run build`.
- Prettier/format verification and `git diff --check`.
- Runtime market-first, opportunity-first, account-first, and position-first
  walkthroughs where the local stack supports them.
- Network and stream inspection for duplicate requests, repeated account/market
  resolution, and subscriptions surviving context destruction.
- Desktop, wide desktop where available, and narrow viewport visual validation.

## Workflow Decision

**B - PARTIAL_IMPLEMENTATION** is the current repository analysis decision.

The account, market, tradability, route context, opportunity provenance, and
market stream timestamp dimensions are sufficiently authoritative for a small
context surface. A broad cross-feature account projection, a universal account
mode/challenge projection, and a single canonical workflow lifecycle are not
available as one authoritative contract and remain deferred unless implementation
finds an additive owning-service field that is strictly necessary.

No ADR is required for the local presentation projection. A new ADR would be
required only if implementation introduces durable global context ownership,
persistent cross-feature context, or a new routing/state-management architecture.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed.
- [ ] Runtime and visual validation completed where available.
- [ ] Code Review completed.
- [ ] Human commit created.
