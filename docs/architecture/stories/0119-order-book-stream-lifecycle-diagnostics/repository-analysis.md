# Story 0119 - Repository Analysis

## Status

Completed for human approval before Implementation Planning.

## Scope Reviewed

This analysis covers Story 0119, ADR-063, the current Market Data Kraken WebSocket
lifecycle, Order Book capture, sequence invalidation, focused tests, and current
runtime evidence. No production code was modified.

DevLog was queried for relevant project context but was unavailable because the
configured provider returned `429 insufficient_quota`. The repository and accepted
ADRs remain authoritative.

## Repository State

- Branch: `main`.
- HEAD and `origin/main`: `0d3b65ea6599e7c396163e4a7353c7ceff11d97b`.
- Worktree: clean before analysis and unchanged after analysis.
- `git fetch origin`: failed because the configured SSH key was rejected.
- Story 0119 is the next valid numeric Story after Story 0118.

## Current Lifecycle

```text
MarketSubscriptionServiceImpl
    -> KrakenDataStreamProvider.subscribe
    -> KrakenDataStreamProvider.connect
    -> ReactorKrakenStreamConnector.connect
    -> WebSocketSession.receive
    -> handleMessage / handleFuturesBook
    -> OrderBookEvidenceCaptureService
    -> OrderBookEvidenceRepository
```

The current Reactor chain is:

```java
ws.receive()
  .map(WebSocketMessage::getPayloadAsText)
  .doOnNext(message -> handleMessage(message, product))
  .then()
```

It is wrapped with:

```java
doOnError(error -> log.error("[KRAKEN] ...", error))
doFinally(signal -> {
    clearRealtimeStates(product);
    setSession(product, null);
    setSubscription(product, null);
})
```

## Confirmed Observability Gaps

- `SignalType` is received by `doFinally` but discarded.
- `doOnError` logs an error but does not correlate it with the active Order Book sequence or last evidence.
- Normal completion and cancellation are not logged distinctly.
- No connection/session diagnostic identity exists.
- Subscription request lifecycle is not logged at the provider boundary.
- Kraken control acknowledgements are logged generically and are not correlated with a subscription identity.
- First accepted snapshot and first accepted delta are not logged as lifecycle milestones.
- `OrderBookEvidenceCaptureService` keeps active sequence state private and exposes no diagnostic snapshot.
- `clearRealtimeStates` invalidates the sequence before any termination context is recorded.
- The invalidation reason remains the generic `order book state invalidated`.
- No automatic reconnect or retry exists, and this Story must not add one.
- No dedicated metrics infrastructure is present in `market-data`; logs are the minimal compatible observability surface.

## Sequence and Trust Boundaries

`OrderBookEvidenceCaptureService` creates an in-memory `ActiveSequence` only after
the snapshot has been durably appended. Updates require the current provider
sequence to be continuous when both values are present. Persistence failures and
provider sequence gaps invalidate the sequence. Stream cleanup also calls
`invalidate`, but currently cannot provide a termination category.

Checksum behavior remains unchanged: a non-null checksum is rejected because the
Kraken checksum algorithm is not implemented; a null checksum is accepted under the
existing sequence-validation limitation. Story 0119 must document and observe this
limitation, not change it.

## Runtime Evidence Reconciled

The current runtime still has one historical Order Book sequence for BTC/USD
perpetual `PF_XBTUSD`, marked `INVALIDATED`. No new sequence exists. The prior
evidence showed 37 snapshots, 2,605,302 deltas, and a final `occurredAt` around
14:56 UTC with invalidation around 15:23 UTC. Current logs contain the initial
subscription but no diagnostic connection termination event, so the transport cause
remains unknown.

## Proposed Minimal Implementation Boundary

Implementation should remain inside `market-data` and should:

1. Add a bounded diagnostic context around the Kraken connection lifecycle.
2. Expose a read-only diagnostic view of active Order Book capture state to the
   provider/capture logging boundary without changing historical identity.
3. Log `STREAM_TERMINATING` before `clearRealtimeStates`, including signal, error
   context, connection/subscription context, and last evidence state when present.
4. Log bounded connection, subscription, snapshot, sequence-start, and cleanup
   milestones.
5. Add focused tests using the existing connector seam for completion/error/cancel
   semantics and cleanup ordering.
6. Add a small Story-local operational runbook with Docker logs and existing SQL
   inspection commands.

No database migration, metrics stack, reconnect behavior, retry operator, checksum
implementation, replay change, or economic-domain change is required by the current
evidence.

## Risks and Decisions

- The exact upstream transport termination cause remains unknown until a future real
  termination is captured.
- Cancellation cause may not be deterministically attributable from Reactor alone;
  diagnostics must report unknown rather than infer a cause.
- Adding diagnostic accessors must not expose mutable sequence state or allow logs to
  alter trust decisions.
- Runtime startup proof may be limited because current subscriptions are externally
  initiated and no natural failure should be forced.

## Recommendation

Repository Analysis recommendation: **READY FOR IMPLEMENTATION PLANNING**.

The Story is implementable as a focused Market Data observability change without an
ADR or schema change. Human approval of this Repository Analysis is required before
creating the Implementation Plan and before modifying production code.
