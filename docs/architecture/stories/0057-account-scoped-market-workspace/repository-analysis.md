# Repository Analysis - Story 0057

## Scope

Story 0057 extends the account-first Decision Workspace with the selected
market's current Market Data context. It must preserve the account-scoped
selection boundary introduced by Story 0056 and reuse the existing market
stream implementation.

## Current Repository Evidence

### Account-scoped context

Story 0056 provides:

* `GET /api/v1/intelligence/decision-context/{accountId}`;
* eligible market ids;
* excluded market decisions;
* selected account profile references;
* Angular `/decision-workspace` route and account-first state.

The current implementation intentionally does not invent broker/instrument
capability rules. Story 0057 must consume the resolved eligible set rather than
querying the global catalogue as a substitute.

### Market Data API

Market Data exposes:

* `GET /api/v1/markets`;
* `GET /api/v1/markets/{marketId}`;
* `GET /api/v1/markets/{marketId}/ohlc`;
* subscription endpoints for ticker, OHLC, order book, and recent trades.

The public Gateway already routes `/api/v1/markets/**` to Market Data.

### Existing Angular market implementation

The current market feature already contains:

* `MarketService` for catalogue, detail, subscriptions, and OHLC history;
* `MarketDataStreamService` for live ticker, OHLC, order-book, and recent-trade
  streams;
* `MarketDetail` with explicit stream bookkeeping and destroy-time cleanup;
* reusable chart, order-book, and recent-trades components;
* current market-state and constraints rendering.

This is the preferred implementation base. A second streaming abstraction or
new provider protocol is not justified.

## Current Gaps

1. Decision Workspace market buttons only select a market and show a placeholder.
2. The selected market is not yet loaded from `MarketService`.
3. The current route does not hydrate account and market state from query
   parameters.
4. Existing live stream cleanup is coupled to the standalone `MarketDetail`
   component and needs safe reuse or extraction.
5. Freshness semantics are visible through timestamps in parts of the existing
   UI but are not represented as an explicit Workspace state model.

## Proposed Responsibility Boundary

* Decision Context determines whether a market may be selected for an account.
* Market Data determines the market's current state and data facts.
* Angular composes the resolved account context and selected market facts.
* Angular must not promote a global market response to account eligibility.
* Risk, intelligence, and execution remain outside this Story.

## Preferred Implementation Shape

Extend the Decision Workspace to:

1. read or validate `accountId` and `marketId` URL state;
2. resolve the account context;
3. reject a market not present in `eligibleMarketIds`;
4. load the market detail;
5. activate the existing stream set for that market;
6. clean up streams on account/market changes and component destruction;
7. render reusable existing market-data components.

The implementation may extract a reusable stream-session service from
`MarketDetail` if that reduces duplication without changing public contracts.

## Implementation Boundary

Included:

* `trading-os-web` Decision Workspace state, URL synchronization, stream
  lifecycle, and market presentation;
* focused tests for account/market transitions;
* backend changes only if an existing contract cannot provide the required
  selected-market data.

Excluded:

* Market Data domain or provider changes;
* account eligibility rules;
* opportunity, Trade Plan, Risk, and execution features;
* standalone market-route redesign.

## Risks and Open Questions

* The existing `MarketDetail` manually tracks four subscriptions. Extracting
  that lifecycle must preserve unsubscribe-on-change and unsubscribe-on-destroy
  behavior.
* The repository does not yet define one global stale threshold for every live
  stream. The implementation should use existing event timestamps and expose
  an honest unavailable state rather than inventing a universal threshold.
* A market can be eligible at context resolution and become non-tradable later.
  The latest Market Data state must remain authoritative and visible.
* URL query parameters can contain stale account/market ids. They must not
  bypass backend context resolution.

## Validation Expectations

* Focused Angular Workspace and market-stream tests.
* Existing Market Data component and service tests remain green.
* Angular `npm run test:ci`.
* Angular `npm run build`.
* Prettier check.
* `git diff --check`.
* Manual authenticated walkthrough for account selection, eligible market
  selection, account switch, market switch, and stream cleanup.

## Architectural Assessment

No new ADR is required if existing Market Data APIs and stream contracts are
reused. An ADR is required if the implementation introduces a new streaming
transport or changes the ownership of market eligibility or market facts.
