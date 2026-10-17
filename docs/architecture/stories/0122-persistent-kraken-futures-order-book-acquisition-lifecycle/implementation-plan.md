# Implementation Plan - Story 0122

## Status

Implementation Plan prepared after explicit human approval of the Repository
Analysis. This plan requires separate human approval before application code
changes begin.

## Implementation Outcome

Add a small configuration-backed startup restoration lifecycle in `market-data`.
The lifecycle will discover configured semantic market identities, validate them
against the canonical market catalogue, and invoke the existing
`MarketSubscriptionService` for `ORDER_BOOK` depth `25` after
`ApplicationReadyEvent`.

The active provider connection remains runtime-only. Configuration is the sole
V1 desired-state authority and is not changed by runtime shutdown or manual
subscription reference changes.

## Desired-State Configuration

Add a new `@ConfigurationProperties` model under a dedicated prefix, with:

- an explicit startup-restoration enablement flag;
- a list of desired subscriptions;
- each entry containing provider, canonical symbol, product type, provider symbol,
  and depth.

The semantic identity is preferred over a database UUID so the configuration
survives fresh market-catalogue installation. The initial configured entry is:

```text
provider: KRAKEN
symbol: BTC/USD
product type: LINEAR_PERPETUAL
provider symbol: PF_XBTUSD
depth: 25
```

The entry is resolved through the existing
`findByProviderAndSymbolAndProductType` repository method. The resolved market's
provider symbol and product type must match the desired entry before any provider
call is attempted.

## Startup Lifecycle

Add a Spring component that listens for `ApplicationReadyEvent` and owns only
startup restoration coordination:

1. If restoration is disabled or no entries are configured, log one bounded
   outcome and return without touching the provider.
2. For each configured entry, emit
   `ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_STARTED` with provider, product,
   symbol, provider symbol, and depth.
3. Resolve and validate the market through `MarketRepository`.
4. Validate that the market is tradable and the depth is supported by the
   canonical subscription service.
5. Call `MarketSubscriptionService.subscribe` with an `ORDER_BOOK` request.
6. Record a successful restoration key in the lifecycle component so repeated
   ready events cannot add another startup reference.
7. Emit either
   `ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORED` or
   `ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_FAILED`.

The lifecycle must call the same service path used by REST. It must not call the
provider or evidence capture service directly.

## Idempotency and Manual REST Behavior

`MarketSubscriptionServiceImpl` already shares equivalent subscriptions using a
reference count. Startup restoration will add one internal desired reference.
An equivalent REST subscription will increment the same reference count and will
not create a second provider stream. A REST unsubscribe removes only its caller
reference while the configured startup reference remains active.

The startup component will guard repeated lifecycle events by a semantic
subscription key. A failed restoration will not be marked successful and will
not create fake active state.

## Shutdown

Add a lifecycle cleanup path for startup-owned runtime references using the
existing `MarketSubscriptionService.unsubscribe` boundary, or the existing
provider disconnect boundary if that is required by the current bean lifecycle.
The cleanup must be idempotent, must not modify desired configuration, and must
not resurrect or mutate persisted sequences. The final implementation must use
the smallest safe shutdown mechanism after confirming Spring's context-close
ordering in the affected module.

## Failure Semantics

Provider, market lookup, validation, connection, or subscription failures will:

- emit one bounded failure event with the failure phase and root cause;
- leave `market-data` running in degraded acquisition state;
- avoid recording the desired entry as active/restored;
- create no sequence and persist no evidence unless a valid provider snapshot is
  subsequently received through the existing path.

No `retryWhen`, periodic retry scheduler, reconnect loop, or automatic
resubscription is allowed.

## Fresh Sequence Semantics

No changes are planned to `OrderBookEvidenceCaptureService` sequence creation.
The existing behavior remains the trust boundary:

```text
fresh provider snapshot -> new sequenceId -> snapshot evidence -> deltas
```

Tests will use the existing capture service/repository tests or a focused
integration seam to prove that a restored stream cannot append to the historical
invalidated sequence.

## Expected Files and Areas

Expected implementation changes are limited to `market-data`:

- `market-data/src/main/java/.../config/` for desired-subscription properties;
- `market-data/src/main/java/.../service/` or `.../lifecycle/` for startup
  restoration and shutdown coordination;
- `market-data/src/main/resources/application.properties` for property binding
  defaults/placeholders;
- `docker-compose.yml` only if repository runtime conventions require exposing
  the new non-secret configuration variables;
- focused tests under `market-data/src/test/java/...`;
- this Story's runbook and implementation report after validation.

No database migration is expected. No changes are planned for Broker Service,
Trading Core, Market Intelligence, fee capture, funding, FeatureSnapshots,
ObservedMarketOutcomes, or economic result contracts.

## Tests

Add focused coverage for:

- configured desired entry discovery;
- no desired entries causing no provider/service call;
- valid Kraken Futures market and depth validation;
- rejection of wrong provider, product, provider symbol, or depth;
- startup restoration using `MarketSubscriptionService`;
- duplicate `ApplicationReadyEvent` remaining one active subscription;
- equivalent REST subscription sharing the existing provider call;
- startup failure producing no fake active state, sequence, or evidence;
- success and failure diagnostic events;
- shutdown cleanup preserving desired configuration;
- fresh snapshot creating a new sequence while the old sequence remains
  invalidated;
- delta-before-snapshot and sequence-continuity regressions.

Existing `MarketSubscriptionServiceImplTest`, provider WebSocket tests, evidence
capture tests, replay tests, controller tests, and application context tests must
remain green.

## Validation Plan

1. Run focused new and affected Market Data tests.
2. Run the complete Market Data module verification command used by the
   repository, expected to be:

   ```text
   cd market-data && ./mvnw verify
   ```

3. Run `git diff --check`.
4. Inspect the complete diff for accidental Story 0121 or unrelated changes.
5. If tests pass and local runtime conventions permit it, perform one controlled
   `market-data` restart only.
6. Without calling the manual subscription POST, verify startup diagnostics,
   Futures connection/subscription, fresh snapshot, new sequence ID, and bounded
   evidence growth using T1/T2 measurements.
7. Requery OHLC, FeatureSnapshots, ObservedMarketOutcomes,
   HistoricalFeeSchedule, HistoricalFundingEvidence, and replayable Order Book
   ranges without modifying any data.

Runtime proof may be reported as blocked if the configured desired subscription
is not enabled in the local deployment or Kraken connectivity cannot be
verified. That does not authorize a manual POST or a claim of operational
success.

## Stop Conditions

Stop and request a new decision if implementation requires:

- a database-backed subscription domain or migration;
- changing REST semantics to persist user intent;
- cross-service orchestration;
- automatic reconnect/retry;
- checksum implementation;
- historical evidence modification;
- changes outside Market Data's subscription/acquisition lifecycle.

## Documentation

Update the Story runbook only if the implementation introduces a required
configuration or operational validation procedure. The Implementation Report
will record the documentation outcome and preserve Story 0121 as a separate
untracked artifact set.

## Approval Boundary

Implementation must not begin until this plan has explicit human approval.
