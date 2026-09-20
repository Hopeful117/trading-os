# Implementation Plan - Story 0057

## Plan Status

Implementation completed. Human code review remains pending.

## Step 1 - Confirm Story 0056 Contract Consumption

Verify the current Decision Workspace contract and frontend state:

* account context resolution;
* eligible market ids;
* query-parameter behavior;
* account-change reset behavior.

Do not use the global market catalogue as a replacement for the resolved
eligible set.

## Step 2 - Design the Stream Session Boundary

Compare two minimal approaches:

1. Extract the existing `MarketDetail` subscription bookkeeping into a reusable
   account-scoped market stream session.
2. Compose the existing child components directly in the Decision Workspace
   while preserving the current cleanup logic.

Choose the smaller approach that avoids a second implementation of stream
subscription and cleanup semantics.

## Step 3 - Implement URL-Driven Selection

Support:

```text
/decision-workspace?accountId=<uuid>&marketId=<uuid>
```

Rules:

* `accountId` must resolve through the account-first context endpoint.
* `marketId` must be present in `eligibleMarketIds`.
* Invalid or stale `marketId` values are cleared without opening streams.
* Account changes clear `marketId` and all selected-market data.

## Step 4 - Implement Market Context Loading

Load the selected market through `MarketService.findById()` and render:

* identity and state;
* ticker;
* OHLC history/live stream;
* order book;
* recent trades;
* constraints;
* timestamps and explicit availability states.

Preserve backend truth. Do not compute account eligibility or Risk locally.

## Step 5 - Implement Stream Lifecycle Tests

Cover:

* no subscriptions before account and market selection;
* eligible market starts the expected subscriptions;
* ineligible market does not start subscriptions;
* changing market unsubscribes the old streams;
* changing account unsubscribes all streams;
* component destruction unsubscribes all streams;
* stream errors render unavailable/error states without fabricated values.

## Step 6 - Validate

Run:

```text
npm run test:ci
npm run build
npm exec -- prettier --check <affected frontend files>
git diff --check
```

Perform the authenticated runtime walkthrough described in the Story.

## Explicit Non-Goals

Do not implement:

* opportunities;
* Active Scan results;
* Trade Plan creation;
* Risk evaluation;
* execution;
* watchlists;
* broker capability filtering beyond the existing context response.
