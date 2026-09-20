# Story 0057 - Account-Scoped Market Workspace

## Metadata

**ID:** `0057`
**Title:** Account-Scoped Market Workspace
**Status:** Draft

---

## Goal

After selecting an account and resolving its decision context, an authenticated
trader can select one eligible market and inspect its current market data
without leaving the account-scoped Decision Workspace.

```text
selected account
    -> resolved eligible markets
    -> selected market
    -> live market context
```

This Story provides the market-context slice only. Opportunities, Trade Plans,
Risk decisions, and execution remain later slices.

---

## Context

Story 0056 introduced the account-first Decision Workspace and the
`GET /api/v1/intelligence/decision-context/{accountId}` contract. The response
contains the selected account context and the current eligible market ids.

Trading OS already has a standalone market exploration flow with:

* market detail loading;
* ticker subscription;
* OHLC history and live candle streams;
* order-book subscription;
* recent-trades subscription;
* market constraints and market-state presentation.

The current market exploration route is global and does not prove that the
market belongs to the selected account context. The Decision Workspace must
use the account-scoped market decision before opening market streams.

---

## Problem

The account-first Workspace currently stops after market eligibility resolution.
The trader cannot inspect the selected market in the same account context.

If the existing global market detail flow is reused without a guard:

* a stale or ineligible market could remain selected after account switching;
* live subscriptions could be opened for a market outside the resolved scope;
* the user could confuse global market exploration with account-specific
  decision context;
* stale, closed, or unavailable market data could be presented as actionable.

---

## Scope

### Included

* Account-scoped market selection inside `/decision-workspace`.
* URL state using:

  ```text
  /decision-workspace?accountId=<uuid>&marketId=<uuid>
  ```

* Validation that the selected market belongs to the latest resolved eligible
  market set.
* Resetting the selected market and all live market streams when the account
  changes or context resolution fails.
* Reuse of existing Market Data APIs and Angular market services/components.
* Display of the selected market identity and account context.
* Display of existing market facts:

  * ticker;
  * last price, bid, ask, and spread;
  * OHLC history and live candle;
  * order book;
  * recent trades;
  * market constraints;
  * market tradability and closure reason.

* Explicit freshness and availability states for each market-data section.
* Safe unsubscribe behavior when the account or market changes.
* Responsive desktop/mobile layout consistent with the current dark dashboard
  design.
* Backend and frontend regression tests for account/market switching and
  stream lifecycle.

### Freshness semantics

The UI must distinguish at least:

* `LIVE` - current stream is active and producing data;
* `RECENT` - latest data exists but no live update is currently observed;
* `STALE` - latest data is too old for the current display contract;
* `UNAVAILABLE` - no usable data exists.

The frontend must display backend/provider timestamps and must not invent a
freshness guarantee. Exact stale thresholds must reuse existing repository
configuration or be explicitly introduced as part of implementation.

---

## Out of Scope

* Active opportunities and Market Intelligence result rendering.
* Trade Plan creation, acceptance, rejection, or replanning.
* Risk evaluation or risk-rule changes.
* Broker execution.
* News, economic calendar, AI Engine, or passive scanning.
* Watchlists or persisted market preferences.
* New market-data provider integrations.
* New realtime transport such as SSE or a second WebSocket protocol.
* Full broker/instrument capability filtering not yet available from
  authoritative contracts; this remains related to Story `0051`.
* Changing standalone `/markets` behavior unless a targeted regression fix is
  required.

---

## Acceptance Criteria

* [ ] A trader cannot select a market before an account context is resolved.
* [ ] Only markets in the latest account-context `eligibleMarketIds` can be
      selected in the Decision Workspace.
* [ ] A market id supplied in the URL but absent from the resolved eligible
      set is rejected or cleared without opening a stream.
* [ ] Changing the account clears the selected market and unsubscribes all
      account-dependent market streams.
* [ ] Changing the selected market unsubscribes the previous market streams
      before activating the new subscriptions.
* [ ] The selected market displays its identity, tradability, closure reason,
      constraints, and last market-state update.
* [ ] Ticker data displays explicit loading, live, unavailable, and error
      states.
* [ ] OHLC, order-book, and recent-trades sections preserve their existing
      backend truth and expose explicit unavailable/error states.
* [ ] Market timestamps and freshness state are visible; stale data is not
      presented as live.
* [ ] A closed or non-tradable market cannot be newly selected from the
      account-scoped eligible list.
* [ ] The workspace does not load opportunities, create Trade Plans, evaluate
      Risk, or expose execution actions in this Story.
* [ ] Existing standalone `/markets` behavior remains unchanged.
* [ ] Focused Angular tests cover account switching, market switching, URL
      validation, state transitions, and stream cleanup.
* [ ] Affected backend/frontend tests, Angular production build, Prettier, and
      `git diff --check` pass.

---

## Constraints

* Story 0056's account-context response remains authoritative for market
  selection.
* Market Data remains authoritative for market facts, tradability, and
  timestamps.
* Angular must not recalculate account eligibility or risk.
* Reuse existing `MarketService` and `MarketDataStreamService` contracts.
* Preserve Observable and async-pipe conventions.
* Do not introduce manual subscriptions when the existing component lifecycle
  pattern can be reused safely.
* Human authority and deterministic decision boundaries remain unchanged.

---

## Architectural Decision

`NEW_ADR_REQUIRED = NO` if the implementation composes the existing account
context and Market Data contracts without moving market or account authority.

An ADR is required if implementation introduces a new realtime architecture,
changes Market Data ownership, or makes the Decision Workspace an authority
for account eligibility.

---

## Relevant ADRs

* `ADR-001` - Trading OS Vision and Human Authority
* `ADR-006` - Market Data and Market State Responsibilities
* `ADR-014` - Trading Decision Pipeline
* `ADR-043` - Account and BrokerAccount Identity
* `ADR-044` - Inter-Service Trust and Actor Propagation

---

## Related Stories

* `0056` - Account-First Market Decision Context
* `0008` - On-Demand Market Snapshot Acquisition
* `0051` - Broker Capabilities and Margin Facts

---

## Relevant Modules

* `market-data`
* `trading-os-web`
* `gateway` only if an existing route requires adjustment

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved.
* [ ] Story scope approved.
* [ ] Implementation completed within this Story's scope.
* [ ] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.
