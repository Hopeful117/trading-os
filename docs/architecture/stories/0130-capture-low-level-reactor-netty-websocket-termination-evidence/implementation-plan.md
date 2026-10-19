# Implementation Plan - Story 0130

## Approval

This plan requires explicit human approval before production code changes begin.

## Overview

Add a passive, connection-scoped Netty observer inside the existing Market Data
Reactor Netty adapter. The observer will capture bounded lower-level evidence,
forward every event unchanged, and expose a snapshot to the existing Story 0129
termination finalization. It will not own cleanup, recovery, sequence state, or
database persistence.

The implementation will enrich the existing `kraken_stream_termination` record
additively. Historical Story 0129 rows will remain unchanged and their new
evidence columns will remain nullable.

## Verified Stack and Hook

The resolved stack is:

```text
Spring Boot 4.1.0
Spring Framework 7.0.8
Reactor Core 3.8.6
Reactor Netty 1.3.6
Netty 4.2.15.Final
Java 21
```

The production adapter uses `ReactorNettyWebSocketClient` backed by
`HttpClient.create()`. The plan will configure a connection-scoped passive
observer through the Reactor Netty connection lifecycle and attach it with:

```text
HttpClient.doOnConnected(...)
    -> Connection.addHandlerLast(...)
```

`addHandlerLast` is the user-pipeline position immediately before Reactor Netty
terminal handlers such as `ReactiveBridge`. The observer will forward every
message and event so Reactor Netty's `WebsocketClientOperations` still receives
and handles the close frame normally.

The placement will be verified by `EmbeddedChannel` forwarding tests and by a
connector-level construction test where practical. If the concrete upgrade
pipeline proves that this hook cannot see decoded `CloseWebSocketFrame` objects,
implementation must stop rather than replacing Spring WebSocket infrastructure.

## Connection Contract and Correlation

Extend the internal `KrakenStreamConnector` contract so the provider explicitly
passes the already-created `connectionId` to the production adapter. The
connector remains a Market Data internal boundary and does not expose Netty
types to domain models or public APIs.

The production adapter creates or obtains a connection-scoped evidence
accumulator and associates it with the physical Netty channel in the connection
callback. The observer receives:

```text
connectionId
evidence accumulator
```

The observer may capture a bounded `nettyChannelId` for diagnostics, but it must
not infer `connectionId` from timing, channel order, or log correlation.

Story 0129 remains responsible for capturing active sequence diagnostics and
persisting the `connectionId` to `sequenceId` relationship.

## Evidence Model

Create a small thread-safe accumulator and immutable snapshot model under the
existing Kraken WebSocket package. The accumulator will use monotonic evidence
updates so generic callbacks cannot overwrite stronger evidence.

Persist only fields justified by the actual stack:

```text
nettyChannelId
websocketCloseObserved
websocketCloseCode
websocketCloseReason
channelInactiveObserved
channelInactiveAt
inputShutdownObserved
tlsCloseObserved
tlsCloseSucceeded
tlsExceptionClass
transportExceptionObserved
transportExceptionClass
transportRootCauseClass
```

Values will be bounded and sanitized using the existing Story 0129 conventions.
No frames, payloads, headers, credentials, secrets, or stack traces will be
stored.

The model will use nullable values for evidence not observed. Historical rows
will remain null rather than being backfilled with false or inferred values.

## Passive Handler Behavior

Implement a `ChannelInboundHandlerAdapter` or equivalent passive handler that:

- records `CloseWebSocketFrame` code/reason without retaining the frame;
- calls `ctx.fireChannelRead(msg)` unchanged for every inbound message;
- records `channelInactive` and calls `ctx.fireChannelInactive()`;
- records transport exceptions and calls `ctx.fireExceptionCaught(cause)`;
- records relevant `SslCloseCompletionEvent` and input-shutdown events;
- calls `ctx.fireUserEventTriggered(evt)` unchanged;
- performs only bounded in-memory operations;
- never closes, disposes, retries, subscribes, unsubscribes, blocks, or writes to
  the database.

The handler will not classify or persist. It only captures evidence.

## Finalization and Classification

At the existing provider `doFinally` boundary:

1. Read and remove the connection-scoped evidence accumulator.
2. Combine the evidence snapshot with the existing terminal signal, exception,
   close status, and local intent.
3. Apply the existing local-intent precedence.
4. Apply specific lower-level evidence only when local intent is absent.
5. Preserve `UNKNOWN_TRANSPORT_COMPLETION` for generic completion/channel
   inactivity without stronger evidence.
6. Build one immutable `KrakenStreamTerminationContext`.
7. Preserve the current cleanup, invalidation, recovery, and asynchronous
   diagnostic persistence ordering.

Classification precedence remains:

```text
APPLICATION_SHUTDOWN
    > EXPLICIT_UNSUBSCRIBE
    > LOCAL_DISPOSE
    > REMOTE_CLOSE when CloseWebSocketFrame is observed
    > NETWORK_FAILURE when transport/TLS failure evidence is observed
    > UNKNOWN_TRANSPORT_COMPLETION
```

The observer must not cause finalization to wait indefinitely. If evidence
arrives after finalization due to a proven callback race, the implementation
must stop for review rather than introduce timing-based persistence or duplicate
records.

## Persistence Changes

Add an additive Flyway migration, likely `V9__kraken_stream_transport_evidence.sql`,
with nullable columns on `kraken_stream_termination`:

```text
netty_channel_id VARCHAR(128)
websocket_close_observed BOOLEAN
websocket_close_code INTEGER
websocket_close_reason VARCHAR(256)
channel_inactive_observed BOOLEAN
channel_inactive_at TIMESTAMP WITH TIME ZONE
input_shutdown_observed BOOLEAN
tls_close_observed BOOLEAN
tls_close_succeeded BOOLEAN
tls_exception_class VARCHAR(255)
transport_exception_observed BOOLEAN
transport_exception_class VARCHAR(255)
transport_root_cause_class VARCHAR(255)
```

The exact final column set must be reduced if implementation proves a candidate
field is unavailable or not useful. Existing rows remain valid and nullable.

Update the termination repository insert path and H2/PostgreSQL persistence
tests. Keep the unique `connection_id` constraint and one-record-per-physical-
connection invariant.

Diagnostic persistence remains asynchronous and best effort. Any persistence or
observer failure must not block cleanup, sequence invalidation, or recovery.

## Files to Modify

Production:

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamConnector.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/ReactorKrakenStreamConnector.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationContext.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnostics.java`

Tests:

- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProviderTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnosticsTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenDataProviderDispatchTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/repository/KrakenStreamTerminationPersistenceTest.java`

## Files to Create

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenTransportTerminationEvidence.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenTransportTerminationEvidenceAccumulator.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenNettyTerminationObserver.java`
- `market-data/src/main/resources/db/migration/V9__kraken_stream_transport_evidence.sql`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenNettyTerminationObserverTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenTransportTerminationEvidenceTest.java`

No changes are planned outside `market-data`, the Story 0130 documentation, and
the additive migration.

## Test Plan

### Passive forwarding

- ordinary messages are forwarded unchanged;
- `CloseWebSocketFrame` is forwarded unchanged and remains owned by downstream
  handlers;
- exceptions are forwarded unchanged;
- channel-inactive events propagate;
- TLS/input user events propagate.

### Evidence capture

- close code and bounded reason are captured;
- channel inactivity is captured without remote-close classification;
- exception and root-cause classes are captured and sanitized;
- TLS close success/failure is captured when constructible;
- input shutdown is captured when emitted.

### Precedence and aggregation

- close frame followed by channel inactive and completion gives `REMOTE_CLOSE`;
- exception followed by channel inactive and completion gives `NETWORK_FAILURE`;
- explicit application shutdown remains `APPLICATION_SHUTDOWN`;
- explicit local disposal remains `LOCAL_DISPOSE`;
- channel inactive or completion without stronger evidence remains unknown;
- specific evidence survives later generic callbacks;
- one connection produces one termination context and durable record.

### Lifecycle and persistence

- evidence is removed after normal, error, unknown, local, shutdown, and setup
  failure paths;
- persistence round-trip supports nullable historical fields;
- diagnostic persistence failure does not block invalidation or recovery;
- existing Story 0129 tests remain green;
- invalidated sequences remain excluded from strict replay.

## Validation Commands

Focused tests first:

```text
./mvnw -q -Dtest=KrakenNettyTerminationObserverTest,KrakenTransportTerminationEvidenceTest,KrakenDataStreamProviderTest,KrakenStreamLifecycleDiagnosticsTest,KrakenStreamTerminationPersistenceTest test
```

Module verification:

```text
./mvnw -q verify
git diff --check
```

Runtime validation after implementation and human-approved deployment:

- record the current active connection and sequence before restart;
- deploy only after focused tests and module verification pass;
- confirm normal startup, observer attachment, fresh snapshot, and continuing
  deltas;
- do not force a termination or change subscriptions;
- report a natural termination if one occurs, without waiting indefinitely;
- do not run the PT1H baseline.

## Risks and Mitigations

- **Handler placement cannot see decoded close frames:** stop before workaround;
  verify with `EmbeddedChannel` and connector-level tests.
- **Frame consumption or reference-count errors:** forward unchanged and test
  downstream ownership explicitly.
- **Generic evidence overwrites specific evidence:** use monotonic accumulator
  fields and precedence tests.
- **Finalization races Netty callbacks:** use a bounded handoff defined during
  implementation; do not use arbitrary sleeps.
- **Observer failure affects recovery:** isolate observer/persistence failures
  and preserve current Story 0129 cleanup ordering.
- **Schema over-expansion:** persist only evidence fields demonstrated useful by
  the actual stack.
- **Historical misattribution:** additive nullable fields and no backfill.

## Approval Required

Implementation Plan completed. Human approval is required before code changes.
