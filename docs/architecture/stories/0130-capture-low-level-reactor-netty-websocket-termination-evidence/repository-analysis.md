# Repository Analysis - Story 0130

## Status

Repository Analysis completed. Implementation requires explicit human approval
of this analysis before an Implementation Plan or code changes.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  deployed runtime configuration.
- Story 0130 defines the approved scope and safety constraints.
- Story 0129 owns durable termination context, connection/sequence correlation,
  classification, cleanup ordering, and best-effort diagnostic persistence.
- Story 0124 owns sequence invalidation and bounded fresh-snapshot recovery.
- `ADR-063` requires fail-closed Order Book sequence boundaries and excludes
  invalidated evidence from strict replay.
- DevLog was queried but unavailable with `429 insufficient_quota`; no decision
  in this analysis relies on unavailable DevLog context.
- No Obsidian vault context was required; repository and accepted ADR evidence
  are sufficient for this implementation boundary.

## Git State

- Branch: `main`
- HEAD: `37f393a17e5c930ac3a3f654ea87a890445d2e40`
- `origin/main`: same commit.
- Pre-existing untracked file preserved unchanged:
  `docs/architecture/reports/v1-functional-audit.md`.
- Story 0130 is the only newly created Story artifact.
- No application source or test changes are present.

## Story Scope

The change is limited to `market-data` Kraken Futures WebSocket acquisition
observability. It must not change:

- sequence invalidation;
- fresh-snapshot recovery;
- strict replay eligibility;
- subscription behavior;
- reconnect policy;
- WebSocket heartbeat or timeout behavior;
- public APIs or unrelated services.

The observer must be passive, bounded, connection-correlated, and subordinate to
existing cleanup and recovery correctness.

## Current Runtime Stack

The resolved dependency versions are:

```text
Spring Boot = 4.1.0
Spring Framework = 7.0.8
Reactor Core = 3.8.6
Reactor Netty = 1.3.6
Netty = 4.2.15.Final
Java = 21
```

The production adapter is:

```text
ReactorKrakenStreamConnector
    -> ReactorNettyWebSocketClient
    -> Reactor Netty HttpClient
    -> WebsocketClientOperations
    -> Netty Channel pipeline
    -> TLS/TCP
```

The current connector creates one `ReactorNettyWebSocketClient` using
`HttpClient.create()` and exposes only:

```java
Mono<Void> connect(URI uri, Function<WebSocketSession, Mono<Void>> handler)
```

The connector contract has no connection metadata callback and no low-level
transport evidence contract.

## Current Lifecycle Boundary

`KrakenDataStreamProvider` creates the `connectionId` before invoking the
connector. The established `WebSocketSession` is stored and the application
combines:

```text
outbound = ws.send(...)
inbound = ws.receive().map(...).doOnNext(...).then()
terminal = Mono.when(outbound, inbound).then(ws.closeStatus())
```

The connector publisher is then observed with `doOnError` and `doFinally`.
Story 0129 finalizes classification and captures active sequence diagnostics in
that `doFinally` callback before cleanup and recovery.

The current boundary receives:

- Reactor `SignalType`;
- terminal exception and root cause when available;
- Spring `CloseStatus` when Reactor Netty exposes one;
- connection ID and product;
- connected/terminated timestamps;
- active sequence diagnostics;
- explicit local intent.

It does not receive raw Netty frames, channel lifecycle events, TLS events, or
input-shutdown events.

## Lower-Level Evidence Boundary

The actual Reactor Netty implementation is `WebsocketClientOperations`, not the
generic Netty `WebSocketClientProtocolHandler`.

The local resolved source shows:

- `CloseWebSocketFrame` is intercepted by `WebsocketClientOperations`;
- its status code and reason are emitted through `receiveCloseStatus()`;
- the frame is not exposed through Spring `WebSocketSession.receive()`;
- `channelInactive` calls `onInboundClose()` and completes the inbound stream;
- a channel close without a close frame can therefore become exception-free
  `ON_COMPLETE`;
- transport exceptions flow through `exceptionCaught` and the inbound error path;
- TLS close completion is exposed as a Netty user event.

The preferred future observation point is a passive inbound handler attached to
the Reactor Netty user pipeline before `ReactiveBridge`, using the supported
`Connection.addHandlerLast(...)` mechanism from a connection lifecycle hook.
The exact handler placement must be verified by focused tests before runtime
deployment.

## Proposed Design Boundary

The smallest coherent design is:

1. Extend the connector-level lifecycle boundary with a connection-scoped
   observer context, without exposing Netty types to the domain or public APIs.
2. Attach one passive Netty handler per physical connection.
3. Store bounded evidence in a thread-safe per-connection accumulator.
4. Forward every message, exception, and user event unchanged.
5. Consume the accumulator only from the existing Story 0129 termination
   finalization path.
6. Remove the accumulator after finalization, including error and setup-failure
   paths.
7. Enrich the existing termination context/persistence only if the selected
   evidence fields require durable storage.

Candidate evidence must be limited to fields supported by the actual stack:

```text
nettyChannelId
webSocketCloseObserved
webSocketCloseCode
webSocketCloseReason
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

Raw frames, payloads, headers, credentials, secrets, and full stack traces are
out of scope.

## Correlation Strategy

The `connectionId` must be supplied explicitly by the connector lifecycle. It
must not be inferred from timestamps or channel ordering.

The likely boundary is a connector method or connection-scoped callback that
receives the already-created `connectionId`. The production adapter can then
associate the channel observer with that ID when the Reactor Netty connection
becomes available.

Story 0129 remains responsible for:

```text
connectionId -> active sequence diagnostics -> sequenceId persistence
```

The low-level observer must not duplicate sequence ownership or inspect Order
Book evidence.

## Classification Rules

Existing local intent remains authoritative:

```text
APPLICATION_SHUTDOWN
    > EXPLICIT_UNSUBSCRIBE
    > LOCAL_DISPOSE
    > REMOTE_CLOSE when CloseWebSocketFrame evidence exists
    > NETWORK_FAILURE when transport/TLS failure evidence exists
    > UNKNOWN_TRANSPORT_COMPLETION
```

`ON_COMPLETE` and `channelInactive` alone remain unknown. Specific evidence must
survive later generic callbacks. A single physical connection must produce one
final termination context and one durable record.

The observer must not classify raw `channelInactive` as TCP FIN or raw exception
absence as a clean close. TCP-level claims remain conservative and limited to
what the actual Netty evidence proves.

## Finalization and Concurrency

The observer must capture only. It must not persist from `channelRead`,
`channelInactive`, `exceptionCaught`, or `userEventTriggered`.

Story 0129 `doFinally` remains the finalization boundary. The implementation
must account for close-frame, exception, TLS event, channel-inactive, and
terminal-signal ordering so that:

- a close frame cannot be downgraded by later `channelInactive`;
- an exception cannot be downgraded by later completion;
- local intent cannot be replaced by transport evidence;
- duplicate finalization cannot create duplicate records;
- diagnostic failures cannot block cleanup, invalidation, or recovery.

If Reactor finalization can race the Netty callback that carries stronger
evidence, the Implementation Plan must define a bounded handoff/finalization
mechanism rather than relying on timing.

## Persistence Boundary

Story 0129 currently persists the termination and sequence correlation in:

```text
kraken_stream_termination
kraken_stream_termination_sequence
```

Historical rows must not be backfilled. Any new columns must be additive and
nullable for historical records. A separate diagnostic table should not be
introduced unless the existing termination model cannot safely hold the bounded
evidence.

Persistence remains asynchronous/best-effort and must not execute on the Netty
event loop. Existing sequence invalidation and recovery ordering remains
unchanged.

## Local Lifecycle Review

Known local paths are already represented by Story 0129:

- application disconnect marks `APPLICATION_SHUTDOWN`;
- recovery connection disposal marks `LOCAL_DISPOSE`;
- unsubscribe marks `EXPLICIT_UNSUBSCRIBE`;
- outer Reactor cancellation remains distinct from `ON_COMPLETE`.

No untracked application-owned direct disposal path was identified. The new
observer must nevertheless capture low-level evidence without converting
framework-generated channel closure into a local intent classification.

## Test Surface

Required focused tests include:

- ordinary message forwarding through an `EmbeddedChannel` or equivalent;
- close-frame forwarding without release or mutation;
- close code/reason capture;
- channel-inactive capture;
- transport exception and root-cause capture;
- TLS close success/failure event capture where constructible;
- input-shutdown event capture where supported;
- explicit local intent precedence;
- close frame followed by channel inactive and completion;
- exception followed by channel inactive and completion;
- generic channel inactive/completion remaining unknown;
- one finalization and one persistence record per connection;
- evidence accumulator cleanup on normal, error, unknown, local, shutdown, and
  establishment-failure paths;
- existing Story 0129 persistence, recovery, invalidation, and strict replay
  regressions.

Tests must prove that observer failure does not block cleanup or recovery and
that no database operation occurs in the Netty callback path.

## Validation Plan

Affected module:

```text
market-data
```

Initial validation should use focused observer and lifecycle tests, followed by
the relevant Market Data verification suite and `git diff --check`.

Runtime deployment is deferred until implementation and tests pass. Runtime
validation must confirm normal startup, observer attachment, fresh snapshot
acquisition, and continued deltas without forcing a termination. A natural
termination may be reported if one occurs, but the implementation must not wait
indefinitely for one.

## Risks and Stop Conditions

Stop and request a new architectural decision if:

- the concrete Reactor Netty pipeline cannot safely expose decoded close frames
  before internal consumption;
- passive forwarding requires replacing Spring WebSocket infrastructure;
- connection correlation requires timing inference;
- implementation would alter protocol handling, backpressure, or reconnect
  behavior;
- evidence persistence would block the event loop or trust cleanup;
- sequence invalidation or strict replay semantics would need to change;
- a schema or public API expansion is required beyond the bounded Story scope.

## Analysis Conclusion

```text
IMPLEMENTATION_SCOPE = MARKET-DATA-ONLY
OBSERVATION_LAYER = REACTOR-NETTY-PASSIVE-CHANNEL-HANDLER
FINALIZATION_LAYER = EXISTING-STORY-0129-TERMINATION-FINALIZATION
CLASSIFICATION_POLICY = SPECIFIC-EVIDENCE-OVER-UNKNOWN-FALLBACK
TRUST_POLICY = PRESERVE-FAIL-CLOSED-SEQUENCE-INVALIDATION
PERSISTENCE_POLICY = ADDITIVE-BOUNDED-BEST-EFFORT
RUNTIME_POLICY = NORMAL-STARTUP-VALIDATION-NO-FORCED-TERMINATION
```

Repository evidence supports implementation planning. Human approval of this
Repository Analysis is required before the Implementation Plan is created.
