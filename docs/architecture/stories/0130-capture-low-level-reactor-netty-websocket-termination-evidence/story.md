# Story 0130 - Capture Low-Level Reactor Netty WebSocket Termination Evidence

## Metadata

**ID:** `0130`
**Status:** Implementation complete - awaiting review
**Related Stories:** `0124`, `0129`
**Related ADRs:** `ADR-063`

## Goal

Passively capture bounded Reactor Netty and Netty lifecycle evidence for Kraken
Futures WebSocket terminations, correlate that evidence with the existing Story
0129 connection lifecycle, and improve future termination attribution without
changing market-data behavior or historical trust semantics.

## Context

Story 0129 is merged, deployed, and runtime-validated. It has persisted three
natural Kraken Futures terminations as:

```text
classification = UNKNOWN_TRANSPORT_COMPLETION
signalType = ON_COMPLETE
closeCode = unavailable
closeReason = unavailable
exceptionClass = unavailable
rootCauseClass = unavailable
```

The current Spring WebFlux abstraction receives a terminal Reactor signal after
lower-level channel evidence may already have been reduced to completion. The
validated runtime stack is:

```text
Spring WebFlux ReactorNettyWebSocketClient
    -> Reactor Netty WebsocketClientOperations
    -> Netty Channel pipeline
    -> TLS/TCP transport
```

The investigation established that a passive handler placed before Reactor
Netty's ReactiveBridge can observe relevant decoded frames and channel events
without consuming them.

## Problem

Story 0129 can preserve the fact that a connection terminated, but it cannot
reliably distinguish a WebSocket close frame, transport exception, TLS event,
input shutdown, or generic channel inactivity when the higher-level publisher
only completes.

## Scope

- Add a passive Reactor Netty/Netty termination observer for the Kraken stream.
- Correlate the observer with the existing `connectionId`.
- Capture bounded evidence for `CloseWebSocketFrame`, `channelInactive`,
  `exceptionCaught`, relevant TLS close events, and input-shutdown events when
  exposed by the actual runtime.
- Forward all messages, exceptions, and user events unchanged.
- Accumulate evidence per physical connection and consume it only during the
  existing Story 0129 termination finalization.
- Preserve local intent precedence and the existing termination classifications.
- Enrich existing termination persistence only when fields are required and
  additive schema changes are justified.
- Ensure diagnostic failures remain subordinate to cleanup, invalidation, and
  recovery.
- Add focused observer, aggregation, precedence, duplicate-prevention, cleanup,
  persistence, and Story 0129 regression tests.
- Validate normal startup and acquisition without forcing a termination.

## Out of Scope

- Changing sequence invalidation, recovery, or strict replay semantics.
- Keeping a sequence active after uncertain transport termination.
- Stitching or reusing invalidated sequences or evidence.
- WebSocket heartbeat, ping scheduling, timeout, keepalive, or reconnect redesign.
- Provider, DNS, proxy, Docker, VPN, or host-network remediation.
- Packet capture or runtime logging changes.
- Consuming, modifying, retaining, or reordering WebSocket frames.
- Database writes from a Netty event loop.
- Backfilling low-level evidence for historical Story 0129 rows.
- Public diagnostics APIs or provider-specific concepts in market-domain models.
- Changes outside Kraken Futures WebSocket acquisition observability.
- PT1H Economic Machinery Baseline execution.
- Forced disconnect, reconnect, unsubscribe, resubscribe, or external provider
  calls intended to provoke the behavior.

## Evidence Model

Use the smallest bounded model supported by the actual stack. Candidate evidence
may include:

```text
connectionId
nettyChannelId
webSocketCloseObserved
webSocketCloseCode
webSocketCloseReason
channelInactiveObserved
channelInactiveAt
inputShutdownObserved
tlsCloseSucceeded
tlsExceptionClass
transportExceptionObserved
transportExceptionClass
transportRootCauseClass
localDisposeObserved
```

Do not persist raw frames, payloads, headers, credentials, secrets, or full
stack traces. Do not claim TCP FIN or TCP RST unless the actual evidence supports
that level of specificity.

## Classification Precedence

Preserve existing local intent authority:

```text
APPLICATION_SHUTDOWN
    > EXPLICIT_UNSUBSCRIBE
    > LOCAL_DISPOSE
    > REMOTE_CLOSE when a CloseWebSocketFrame is observed
    > NETWORK_FAILURE when transport/TLS failure evidence is observed
    > UNKNOWN_TRANSPORT_COMPLETION
```

`ON_COMPLETE` and `channelInactive` alone must remain unknown. A generic event
must not overwrite stronger close-frame, exception, TLS, or local-intent
evidence. One physical connection termination must produce one final durable
termination record.

## Trust Invariants

The following behavior remains unchanged:

```text
unknown transport continuity
    -> active Order Book sequence invalidated
    -> fresh provider snapshot required
    -> new sequence created
```

Invalidated sequences remain excluded from strict historical replay. Diagnostic
observer failure must never block cleanup, sequence invalidation, recovery, or
fresh-snapshot acquisition.

## Acceptance Criteria

- [ ] A passive Netty observer is attached at a verified safe pipeline position.
- [ ] Ordinary messages and lifecycle events are forwarded unchanged.
- [ ] `CloseWebSocketFrame` is observed without consumption or release by the
      observer, with bounded close code and reason captured when available.
- [ ] `channelInactive` is captured as generic channel evidence only.
- [ ] Transport exceptions and bounded root-cause metadata are captured when
      exposed.
- [ ] Relevant TLS close/failure and input-shutdown events are captured when
      exposed by the actual stack.
- [ ] Observer evidence is correlated to the existing `connectionId` without
      timing-based inference.
- [ ] Evidence is accumulated per connection and finalized once through Story
      0129; duplicate callback records are impossible.
- [ ] Specific evidence cannot be downgraded by later generic callbacks.
- [ ] Explicit local shutdown, unsubscribe, and disposal intent remains
      authoritative.
- [ ] `REMOTE_CLOSE` requires actual close-frame evidence.
- [ ] `NETWORK_FAILURE` requires transport or TLS failure evidence.
- [ ] `UNKNOWN_TRANSPORT_COMPLETION` remains the conservative fallback.
- [ ] No observer callback performs database access or blocking work on the
      Netty event loop.
- [ ] Evidence state is removed after normal, error, unknown, local, shutdown,
      and connection-establishment failure paths.
- [ ] Existing cleanup, sequence invalidation, recovery, and strict replay
      semantics are unchanged.
- [ ] Invalidated evidence remains non-replayable.
- [ ] Focused tests cover forwarding, event capture, classification precedence,
      duplicate prevention, cleanup, persistence, and Story 0129 regression.
- [ ] Existing Story 0129 tests and relevant Market Data verification pass.
- [ ] Runtime starts and acquires normally after deployment without a forced
      termination.
- [ ] No historical Story 0129 rows are backfilled.

## Validation

- Inspect the resolved Reactor Netty and Netty versions and actual pipeline.
- Run focused observer and lifecycle tests first.
- Run the relevant Market Data module tests and verification suite.
- Run `git diff --check`.
- Perform normal deployment validation only after implementation and tests pass.
- Observe a natural termination if one occurs; do not wait indefinitely and do
  not force one.
- Report whether a natural event captured close-frame, exception, TLS,
  input-shutdown, or only generic channel evidence.

## Runtime Safety

The implementation must not:

- restart `market-data` before implementation and test validation are complete;
- force a WebSocket disconnect or reconnect;
- modify subscriptions or Order Book state;
- change runtime configuration or logging configuration;
- run migrations destructively;
- execute packet capture or external provider calls;
- execute the PT1H Economic Machinery Baseline.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Passive observer implemented and correlated with `connectionId`.
- [ ] Story 0129 consumes accumulated evidence during finalization.
- [ ] Evidence persistence is additive and bounded if schema changes are needed.
- [ ] Event-loop, memory, duplicate, and failure-safety constraints are tested.
- [ ] Order Book trust, recovery, and strict replay behavior are unchanged.
- [ ] Documentation reconciliation completed.
- [ ] Relevant tests and module validation pass.
- [ ] Runtime startup validation passes without forced termination.
- [ ] Natural-event validation is recorded when available, or explicitly marked
      pending.
- [ ] Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
