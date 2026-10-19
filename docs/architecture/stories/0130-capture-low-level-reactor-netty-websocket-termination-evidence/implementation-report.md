# Implementation Report - Story 0130

## Result

Implemented passive, bounded Reactor Netty/Netty termination evidence capture in
`market-data` and integrated it with the existing Story 0129 termination
finalization without changing Order Book trust, invalidation, recovery, or strict
replay semantics.

## Implementation

- Added a connection-scoped `ChannelInboundHandlerAdapter` attached through
  `HttpClient.doOnConnected(...).addHandlerLast(...)`.
- Forwarded ordinary messages, close frames, exceptions, channel-inactive
  signals, TLS events, and input-shutdown events unchanged.
- Captured bounded close code/reason, Netty channel ID, channel inactivity,
  input shutdown, TLS close result, and transport exception class metadata.
- Added a synchronized monotonic accumulator and immutable evidence snapshot.
- Correlated evidence through the existing `connectionId`; no timing or channel
  ordering inference is used.
- Consumed and cleared evidence once at the existing `doFinally` boundary.
- Preserved local intent precedence and conservative unknown completion fallback.
- Added nullable additive transport-evidence columns through
  `V9__kraken_stream_transport_evidence.sql`; historical rows are not backfilled.
- Kept diagnostic persistence best-effort and off the Netty event loop.
- Isolated evidence read/clear failures so they cannot interrupt cleanup or
  recovery.

## Files

Production:

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenNettyTerminationObserver.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenTransportTerminationEvidence.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenTransportTerminationEvidenceAccumulator.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/ReactorKrakenStreamConnector.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamConnector.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationContext.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationRepository.java`
- `market-data/src/main/resources/db/migration/V9__kraken_stream_transport_evidence.sql`

Tests:

- `KrakenNettyTerminationObserverTest`
- `KrakenTransportTerminationEvidenceTest`
- `KrakenDataStreamProviderTest`
- `KrakenDataProviderDispatchTest`
- `KrakenStreamTerminationPersistenceTest`

## Validation

Passed:

```text
./mvnw -q -DskipTests compile
./mvnw -q -Dtest=KrakenNettyTerminationObserverTest,KrakenTransportTerminationEvidenceTest,KrakenDataStreamProviderTest,KrakenStreamLifecycleDiagnosticsTest,KrakenStreamTerminationPersistenceTest test
./mvnw -q verify
git diff --check
```

Coverage includes:

- close-frame forwarding and bounded metadata capture;
- exception, TLS, input-shutdown, and channel-inactive capture;
- unchanged user-event forwarding;
- monotonic evidence aggregation;
- `connectionId` correlation and one-time evidence cleanup;
- additive persistence round-trip and nullable historical fields;
- existing Story 0129 cleanup, recovery, persistence, and dispatch regressions.

The test logs contain expected stack traces from existing stream-error,
malformed-message, and diagnostic-persistence-failure tests; Maven completed
successfully.

## Runtime Validation

The `market-data` image was rebuilt and only the `market-data` container was
recreated after focused tests and module verification passed. No other service
was restarted, and no forced disconnect, unsubscribe, resubscribe, subscription
mutation, packet capture, or PT1H baseline was performed.

Startup validation passed:

- Flyway version `9` (`kraken stream transport evidence`) applied successfully.
- `MarketDataApplication` started successfully.
- The desired Kraken Futures `PF_XBTUSD` order-book subscription was restored.
- Connection `d97ee8b4-c373-4893-bd65-a28de60b6b97` connected successfully.
- A fresh Order Book sequence `db4f8e7b-bca5-49ef-8bc8-c56856850d15` was started
  and its snapshot was accepted.
- The container remained running without application errors during the passive
  observation window.

A 60-second passive observation window produced no natural termination. No
termination was forced to obtain evidence.

```text
RUNTIME_STARTUP_VALIDATION = PASSED
RUNTIME_TERMINATION_VALIDATION = PENDING_NATURAL_EVENT
```

## Context Limitations

- DevLog was unavailable with `429 insufficient_quota`; implementation relied on
  the repository and accepted ADRs.
- The pre-existing untracked file
  `docs/architecture/reports/v1-functional-audit.md` was preserved unchanged.
- No commit or push was performed. Runtime deployment was limited to rebuilding
  and recreating the `market-data` container.

## Status

Implementation complete. The independent code review findings were addressed,
the follow-up targeted tests and full `market-data` verification pass, and normal
runtime startup/acquisition validation passed. Natural termination evidence and
commit remain pending.
