# Repository Analysis - Story 0134

## Status

Completed - pending human approval.

## Repository Baseline

- Branch: `main`
- HEAD: `d49ecbe5c39128d8268c7897b870f5610f705169`
- Recent merged work includes Story 0132 sidebar navigation and Story 0133
  Research integrity observability.
- Pre-existing untracked files:
  - `docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`
  - `docs/architecture/reports/v1-functional-audit.md`
- Both untracked files are preserved. The functional audit is protected.
- Story identifier `0134` was not occupied before this Story artifact was
  created.

## Authoritative Context

The approved UX investigation recommends a contextual workspace shell around
the existing Decision Workspace. It explicitly rejects a terminal replacement,
Dashboard replacement, generic global state, and a new backend orchestration
service.

Relevant accepted architecture confirms:

- ADR-001: Trading OS is a human-controlled decision-support platform.
- ADR-014: deterministic analysis, Market Intelligence, Risk, human
  validation, and broker execution remain separate pipeline layers.
- ADR-031: planning context must not become financial authorization context.
- ADR-043: financial Account identity is distinct from BrokerAccount routing
  identity.
- ADR-047: manual and opportunity Trade Plans share the same Risk and execution
  lifecycle; a frontend proposal is not an execution command.

DevLog applicability was checked and failed with provider `429
insufficient_quota`. No DevLog context was used as authority.

## Existing Context Contracts

### Account

`AccountService.getAccounts()` exposes account identity, name, base currency,
risk profile metadata, and Trade Planning profile metadata. The frontend account
model does not expose a direct execution mode or challenge object.

### Decision Context

`DecisionContextService.resolve(accountId)` calls the existing Market
Intelligence decision-context endpoint. Its response contains:

- authoritative account projection;
- market identity/provider projections;
- eligible market IDs;
- exclusion reasons;
- `resolvedAt`.

This is sufficient for account-scoped market selection and account context. It
does not provide a universal account mode/challenge projection.

### Market

`MarketService.findById(marketId)` returns normalized market identity, provider,
product type, market state, tradability, closure reason, last update, and market
constraints. This is the authoritative source for market display and
tradability.

### Market Streams

Decision Workspace already owns ticker, OHLC, order-book, and recent-trades
subscriptions. It exposes stream states and derives `LIVE`, `RECENT`, `STALE`,
or `UNAVAILABLE` from event timestamps. This freshness logic is local to the
workspace and should be reused or extracted only if necessary; a new context
component must not create duplicate subscriptions.

### Opportunity

Opportunity Detail resolves the Opportunity and its market. Existing navigation
to Decision Workspace carries `opportunityId`, `marketId`, and `accountId` query
parameters when available. Decision Workspace resolves the Opportunity from
the authoritative Opportunity service and preserves the provenance display.

### Trade Plan and Risk

The current frontend exposes Trade Plan preparation and execution lifecycle
actions, but the Decision Workspace template does not itself own the canonical
Trade Plan/Risk status display. This Story must not create inferred Risk or
execution stages. A presentation stage can safely cover only route/context
states such as exploring, reviewing opportunity, selecting market, and
preparing a proposal unless existing authoritative lifecycle data is explicitly
available to the component.

## Existing Entry Workflows

### Market-first

Markets and Market Detail work without an account. Market Detail knows the
normalized `marketId` and currently owns market streams. It has no existing link
to Decision Workspace. A context-preserving link can carry `marketId`, but
Decision Workspace currently requires account context before it can verify
eligibility and open streams.

### Opportunity-first

Opportunity Detail knows Opportunity identity, market identity, and sometimes
account identity. It already links to Decision Workspace with query parameters.
This transition is the strongest existing context-preservation path and should
remain authoritative.

### Account-first

Decision Workspace resolves accounts, calls the existing Decision Context
contract, filters eligible markets, resolves a selected market, and only then
opens market streams. This is the canonical account-scoped workflow.

### Position-first

Positions are account-scoped and expose position market symbols, current prices,
risk, protection, and close/reconciliation actions. They do not currently retain
a normalized market ID in the visible template contract. A direct transition to
Decision Workspace cannot be added safely without authoritative market identity
and a clear semantic purpose. Position-first remains monitoring/management-first;
direct Decision Workspace navigation is deferred unless the contract supports it.

## Proposed Implementation Slice

The smallest coherent implementation is:

1. Add a compact context presentation component local to Decision Workspace.
2. Feed it the existing account projection, selected market projection,
   tradability, stream freshness, and route-derived Opportunity context.
3. Derive only a limited presentation workflow label from current route and
   component state. Do not introduce a domain lifecycle enum or global store.
4. Preserve existing Decision Workspace query-parameter restoration and
   account-before-stream sequencing.
5. Add a Market Detail -> Decision Workspace link only if it can carry market
   identity without forcing an account before generic exploration.
6. Retain the existing Opportunity Detail -> Decision Workspace transition.
7. Do not add a Positions -> Decision Workspace link in this slice.

## State and URL Strategy

The existing Decision Workspace query parameters are the correct context
transport for `accountId`, `marketId`, and `opportunityId`. They preserve refresh
and browser navigation and avoid a generic global state store.

Account ID is already present in the route query for account-scoped workflows.
It should not be added to generic Market Detail URLs unless the existing route
already has a legitimate account context. Market identity and Opportunity
identity may remain URL-addressable because they identify the current internal
workspace view and are not secrets.

## Backend Assessment

No backend change is currently required. Existing contracts provide the
authoritative minimum for the partial decision:

- account identity and planning/risk metadata;
- normalized market identity and tradability;
- Decision Context eligibility and resolution timestamp;
- market event timestamps and stream state;
- Opportunity identity and provenance.

Account execution mode and challenge context are not available as one frontend
projection. They are deferred rather than inferred from provider names, account
labels, or route state.

## Risk and Authority Assessment

The implementation must not:

- translate Opportunity score or Trend Context into Risk approval;
- calculate market eligibility outside the existing Decision Context response;
- treat stream presence as execution readiness;
- create or authorize an Execution Intent;
- merge analytical freshness with account or Risk freshness;
- expose BrokerAccount identity as financial Account identity.

The context presentation may display authoritative state and route-derived
presentation stage only.

## Stream and Performance Assessment

Decision Workspace owns four market subscriptions and explicitly clears them on
market/account changes and component destruction. A separate context header must
consume existing projected values rather than subscribe directly to Market Data.

Market Detail also owns its own subscriptions. Navigating from Market Detail to
Decision Workspace naturally destroys the source component and its streams; no
cross-page stream sharing is justified for this slice. Runtime validation must
check that old streams are unsubscribed and that the new workspace does not
duplicate subscriptions.

## UX and Accessibility Assessment

The context surface should be compact and appear before dense evidence in
Decision Workspace. It must use semantic labels, visible focus, text/state
indicators not dependent on color alone, and explicit unknown/unavailable
values. It should not add a large hero region or materially increase vertical
growth.

Narrow screens should retain a readable compact summary. A full mobile redesign
is deferred.

## Test Impact

Expected focused coverage:

- context component account, market, tradability, freshness, unknown, and
  unavailable rendering;
- route-derived market and Opportunity context;
- account and market changes clearing old context and streams;
- existing Decision Workspace query-parameter restoration;
- Market Detail navigation if added;
- Opportunity Detail navigation regression;
- no Angular-derived Risk or execution authorization state.

Expected repository validation:

- Angular focused tests;
- `npm run test:ci`;
- `npm run build`;
- formatting verification;
- `git diff --check`;
- runtime workflow and network/subscription inspection where the stack supports
  it.

## Decision

**B - PARTIAL_IMPLEMENTATION.**

Implement the authoritative account, market, tradability, route context,
Opportunity provenance, and existing market-stream freshness subset. Defer
account mode/challenge context and any universal canonical workflow lifecycle
that cannot be derived from existing authoritative contracts.

No ADR is required for a local typed presentation projection. Revisit ADR need
only if implementation introduces durable global context ownership, persistent
cross-feature context, or a new routing/state-management architecture.

## Approval Gate

This Repository Analysis requires explicit human approval before an
Implementation Plan is produced. No production implementation has started.
