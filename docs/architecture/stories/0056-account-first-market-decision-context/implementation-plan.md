# Implementation Plan - Story 0056

## Plan Status

Implementation completed. Human code review remains pending.

## Step 1 - Confirm the Existing Eligibility Boundary

Inspect the Active Scan scope-resolution application service and its adapters to
determine whether its deterministic account/market decisions can be reused by
the Decision Workspace without changing Active Scan semantics.

Required outputs:

* selected application service and repository interfaces;
* account ownership path;
* market catalogue path;
* eligibility reason vocabulary;
* identified contract reuse or required contract extension.

## Step 2 - Define the Account Context Contract

Choose the smallest contract that supports:

* selected account identity;
* canonical account facts needed by the Workspace;
* effective profile references/status;
* eligible markets;
* excluded markets and reasons;
* resolution timestamp.

The contract must not expose secrets or require frontend interpretation of raw
Risk rules.

Preferred options, in order:

1. Reuse the existing scope-resolution application service behind a clearly
   documented account-context response if the semantics are compatible.
2. Extract a shared deterministic account-market eligibility service and keep
   Active Scan as one consumer.
3. Introduce a new authority only after an ADR is approved.

## Step 3 - Implement Backend Ownership and Contract Tests

Cover:

* owned account succeeds;
* unknown account fails;
* account owned by another user fails;
* globally unknown market is excluded;
* non-tradable market is excluded;
* account-incompatible market is excluded when authoritative facts support the
  decision;
* duplicate requested markets do not duplicate results;
* no Risk Domain invocation occurs;
* existing Active Scan behavior remains compatible.

## Step 4 - Implement Angular Account-First State

Introduce a discriminated reactive view state for:

* loading accounts;
* no accounts;
* account selection required;
* resolving account context;
* context ready;
* no eligible markets;
* context error.

The selected account must drive market-context loading. Changing it must clear
the selected market and prevent stale market data from remaining visible.

## Step 5 - Add Focused Workspace Entry Wiring

Add only the minimum entry state needed to demonstrate the account-first
behavior. Do not implement the complete market/intelligence Workspace in this
Story.

If a route is introduced, it should preserve the proposed URL shape:

```text
/decision-workspace?accountId=<uuid>&marketId=<uuid>
```

`marketId` must be ignored or rejected until the account context is valid.

## Step 6 - Validate

Run the affected independent service checks, Angular tests/build, and:

```text
git diff --check
```

Perform an authenticated runtime walkthrough and record whether multiple
account contexts are available for cross-account validation.

## Explicit Non-Goals

Do not implement:

* live market streams in the new Workspace;
* opportunity rendering;
* Trade Plan creation;
* Risk evaluation;
* broker execution;
* watchlists;
* AI, news, or passive scanning.
