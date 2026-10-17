# Repository Analysis - Story 0122

## Status

Repository Analysis completed. Implementation is not authorized until this
analysis and the subsequent Implementation Plan receive explicit human
approval.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  runtime behavior.
- `ADR-063` governs durable Order Book evidence, sequence identity, replay, and
  fail-closed recovery semantics.
- `ADR-064` remains an economic boundary guardrail; no economic behavior is in
  scope.
- Story `0121` remains blocked with `CHECKSUM_SPECIFICATION_NOT_ESTABLISHED`.
  This Story must not implement checksum behavior.
- DevLog was queried and returned `429 insufficient_quota`; no conclusion in
  this analysis relies on unavailable DevLog context.
- Obsidian was not consulted because the repository and accepted ADRs provide
  the relevant authoritative context.

## Git State

- Branch: `main`
- `HEAD`: `8ba372c3f42dc58121cc06f0673a62ede643051e`
- `origin/main`: `8ba372c3f42dc58121cc06f0673a62ede643051e`
- `git fetch origin`: completed successfully.
- Worktree before this Story contained only the pre-existing untracked Story
  `0121` directory.
- Story `0121` was not modified or absorbed into Story `0122`.
- Story `0122` analysis artifacts are the only new files created by this work.
- No application files, migrations, runtime databases, or historical evidence
  have been modified.

## Current Subscription Architecture

The canonical path is:

```text
MarketController
    -> MarketSubscriptionService
    -> MarketSubscriptionServiceImpl
    -> MarketDataStreamProvider
    -> KrakenDataStreamProvider
    -> ReactorKrakenStreamConnector
    -> Kraken WebSocket
```

`POST /api/v1/markets/{marketId}/subscriptions` invokes
`MarketSubscriptionService.subscribe`. The DELETE endpoint invokes the matching
unsubscribe operation.

`MarketSubscriptionServiceImpl` validates the request and market, then tracks
active subscriptions in an in-memory `ConcurrentHashMap` keyed by market,
stream type, and parameters. An `AtomicInteger` shares one provider subscription
between equivalent callers. The provider is started only when the count changes
from zero to one, and stopped when the last reference is removed.

The current REST semantics are therefore an immediate runtime subscription
request, not a durable desired-state declaration.

## Provider and Sequence Ownership

`KrakenDataStreamProvider` owns provider connection sessions, provider stream
registrations, subscription request emission, inbound message handling, and
runtime cleanup. It connects lazily from `subscribe` when the product session is
not already open.

`OrderBookEvidenceCaptureService` owns active in-memory sequence diagnostics and
creates a new `sequenceId` only when a valid provider snapshot is accepted. It
rejects deltas without an active sequence and invalidates the sequence on
continuity or persistence failure. The repository persists sequence status and
immutable evidence; it does not restore active runtime sequences after process
restart.

No Reactor `Disposable`, `Flux`, `Mono`, WebSocket session, or connection object
is persisted.

## Runtime and Configuration Evidence

`market-data` enables Spring scheduling through `MarketDataApplication`, but no
startup listener currently restores Order Book subscriptions. Existing
configuration covers Kraken endpoints, OHLC capture, and Order Book evidence
capture only:

```text
order-book.evidence.capture.enabled
order-book.evidence.capture.provider-symbol
order-book.evidence.capture.depth
```

These settings do not express a desired market subscription. Runtime
investigation confirmed that after the latest `market-data` restart OHLC capture
continued, while no Futures WebSocket connection, subscription, snapshot, or
new Order Book sequence was observed.

## Desired-State Options

### Configuration-backed desired subscription

Add a bounded configuration property containing the desired market ID and depth
(implemented as a list-shaped model so the domain can later support multiple
markets). Restore it from an application-ready lifecycle hook.

Advantages:

- smallest V1 change;
- survives process restart without a migration;
- explicit operational authority;
- supports intentional disablement through configuration;
- easy to test without provider or database orchestration.

Tradeoff:

- changing desired scope requires configuration deployment/restart rather than a
  durable user-facing subscription command.

### Database-backed desired subscription

Add a persistent subscription-intent entity and migration.

This would support runtime user control and richer multi-market ownership, but it
would introduce a new domain, migration, authority model, REST semantics, and
cross-cutting lifecycle behavior that are not required by this bounded Story.

### Reuse an existing durable subscription concept

No semantically correct durable subscription-intent model exists in the current
repository. The existing market catalogue is market identity, not acquisition
intent, and active subscriptions are runtime-only.

## Selected V1 Design

Select a configuration-backed desired subscription source. The configuration
should contain the bounded desired market/depth and an explicit enablement flag.
The persisted/configured semantic intent is separate from the active runtime
reference held by `MarketSubscriptionServiceImpl`.

The configured market must be loaded from `MarketRepository` and validated before
subscription. The implementation must verify the configured market is Kraken,
has product type `LINEAR_PERPETUAL`, has provider symbol `PF_XBTUSD`, is
tradable, and uses supported depth `25`. Provider/product/symbol facts must come
from the canonical market model and configuration validation, not from a second
provider-specific path.

The configuration authority means a manual DELETE releases only the caller's
runtime subscription reference. It cannot override an enabled configured desired
subscription during the same process. Disabling the configured intent and
restarting is the V1 stop mechanism. This avoids contradictory REST/configuration
authorities and should be documented in the implementation report/runbook.

## Startup Lifecycle Recommendation

Use an `ApplicationReadyEvent` listener or an equivalent Spring lifecycle
component after the application context, database, repository, and provider
beans are ready.

For each configured desired subscription:

```text
application ready
    -> emit restore-started diagnostic
    -> load market
    -> validate provider/product/symbol/depth/tradability
    -> skip if this lifecycle instance already restored the key
    -> call MarketSubscriptionService.subscribe
    -> record the successful runtime reference
    -> emit restored diagnostic
```

Failures should be logged with market, provider, product, provider symbol, depth,
and failure phase, then leave the service running in a degraded acquisition
state. The lifecycle must not fabricate active state, create a sequence, or
persist evidence. No retry loop is authorized; a later explicit lifecycle
invocation may be tested for idempotency but must not run periodically.

Normal shutdown should release startup-owned runtime references or close the
provider through the existing lifecycle boundary while leaving configuration
unchanged. The old sequence remains invalidated and cannot be reused.

## Fresh Sequence Semantics

The existing capture service already provides the required boundary:

```text
fresh provider snapshot -> new sequence ID -> snapshot evidence -> deltas
```

Startup restoration must use the existing provider subscription path and must not
call the capture service directly. The previous persisted invalidated sequence
must remain immutable. The implementation must prove with tests that the new
sequence ID differs and that no delta can append to the old sequence.

## Test Scope

Add focused tests for:

- desired configuration discovery;
- valid Futures market validation;
- no desired configuration causing no provider call;
- startup restoration invoking the canonical service;
- repeated startup event remaining idempotent;
- equivalent REST subscription sharing the provider stream;
- startup provider failure producing no fake active state, sequence, or evidence;
- startup diagnostics for success and failure;
- normal shutdown cleanup while desired configuration remains unchanged;
- fresh snapshot/new sequence and old-sequence immutability regressions;
- existing sequence continuity, pre-snapshot delta, replay, and manual
  subscription tests remaining green.

## Risks and Stop Conditions

Stop and request a new architectural decision if implementation requires:

- a database-backed subscription domain or migration;
- changing public API semantics from runtime action to durable user intent;
- cross-service desired-state orchestration;
- automatic reconnect/retry behavior;
- checksum implementation;
- changes to historical evidence, Market Intelligence, fees, funding, or
  economic results.

## Recommendation

```text
ARCHITECTURE_DECISION: CONFIGURATION_BACKED_STARTUP_RESTORATION
DATABASE_MIGRATION: NO
IMPLEMENTATION_SCOPE: MARKET-DATA ONLY
AUTOMATIC_RECONNECT: NO
CHECKSUM: NO
```

The implementation can proceed after explicit human approval of this Repository
Analysis, followed by a separately approved Implementation Plan.
