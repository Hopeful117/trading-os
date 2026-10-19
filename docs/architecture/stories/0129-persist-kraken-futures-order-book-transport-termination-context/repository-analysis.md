# Repository Analysis - Story 0129

## Status

Repository Analysis completed. Implementation requires explicit human approval
of this analysis before code changes.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  runtime behavior.
- `ADR-063` governs Order Book evidence ownership, known-at semantics, sequence
  boundaries, fail-closed invalidation, and strict replay.
- Story `0119` established lifecycle diagnostics but explicitly excluded
  diagnostic persistence and database migrations.
- Stories `0122` and `0124` established the current persistent acquisition,
  invalidation, and bounded recovery behavior that this Story must preserve.
- DevLog was queried for relevant prior context and returned `429
  insufficient_quota`; no conclusion in this analysis relies on unavailable
  DevLog context.

## Git State

- Branch: `main`
- HEAD: `aae3b79a53f87fd87721a1d8e7e26f72538cffea`
- `origin/main`: same commit after a successful fetch.
- Pre-existing untracked file preserved unchanged:
  `docs/architecture/reports/v1-functional-audit.md`.
- No tracked or staged application changes were present before this analysis.

## Current Runtime Boundary

The relevant path is:

```text
ReactorNettyWebSocketClient
    -> ReactorKrakenStreamConnector
    -> KrakenDataStreamProvider
    -> KrakenStreamLifecycleDiagnostics
    -> OrderBookEvidenceCaptureService
    -> OrderBookEvidenceRepository
    -> historical_order_book_sequence / historical_order_book_evidence
```

`KrakenStreamConnector` exposes only:

```java
Mono<Void> connect(URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler)
```

The production adapter delegates directly to
`ReactorNettyWebSocketClient.execute(...)`. It does not currently expose a
provider-neutral close-event or transport metadata contract.

`KrakenDataStreamProvider` receives the established `WebSocketSession`, combines
outbound sending and inbound receiving, and records the terminal Reactor signal
from `doFinally`. It currently passes only:

- connection ID;
- product;
- Reactor `SignalType`;
- terminal `Throwable`, when present;
- active sequence diagnostics captured before cleanup.

`WebSocketSession` lifecycle access is sufficient for local intent tracking and
session state, but the current `KrakenStreamConnector` boundary does not prove
that an `onComplete` originated from a remote close. Therefore an exception-free
`ON_COMPLETE` must remain an unknown transport completion unless reliable close
metadata is available through the actual client API.

## Current Diagnostics

`KrakenStreamLifecycleDiagnostics` already provides bounded, sanitized logging for:

- connection lifecycle;
- subscription requests and confirmations;
- snapshots;
- terminal signal, exception, root cause, and active sequence context;
- cleanup;
- bounded recovery.

Exception messages are sanitized and capped at 256 characters. The diagnostic
record currently contains Reactor signal and exception context but no persisted
termination classification, close status/reason, intentional lifecycle reason,
or durable connection termination record.

## Current Trust and Recovery Behavior

`KrakenDataStreamProvider` emits termination diagnostics before
`clearRealtimeStates(product)`. The cleanup path clears live state and calls
`OrderBookEvidenceCaptureService.invalidate(...)` for configured Order Book
scopes. Futures recovery remains bounded, suppressed during intentional
disconnect or unsubscribe, and requires a fresh snapshot before reporting
success.

`OrderBookEvidenceCaptureService` owns active sequence state and exposes a
diagnostic snapshot containing:

- sequence ID;
- market ID;
- depth;
- provider sequence;
- local ordinal;
- last occurred timestamp;
- last known timestamp.

These values are sufficient for the requested sequence correlation and must be
captured before cleanup removes the active sequence.

`OrderBookEvidenceRepository` persists immutable evidence and sequence lifecycle
metadata. The existing `historical_order_book_sequence` table contains status,
invalidated timestamp, and invalidation reason, but no transport termination
context. The current replay query depends on sequence status and trusted
evidence; Story 0129 must not change that behavior.

## Recommended Design Boundary

The smallest coherent implementation is Market Data only:

- add a bounded provider-neutral termination classification and context model;
- classify from explicit local intent, error signal, and available close metadata;
- preserve unknown classification for exception-free completion without proof;
- persist termination context in an additive diagnostics table or an additive
  sequence-linked structure after reviewing the existing schema constraints;
- correlate one physical connection termination with connection ID and any
  active sequence IDs captured before cleanup;
- add repository methods that are best-effort for diagnostics and cannot block
  invalidation or recovery;
- extend structured logs with classification and bounded metadata;
- leave evidence rows, sequence status transitions, replay SQL, subscription
  contracts, and recovery policy unchanged.

Potential implementation files are:

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnostics.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamConnector.java`
  only if a metadata-capable callback boundary is required and remains
  provider-neutral.
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
  only if termination context is stored alongside existing evidence ownership.
- a new Market Data diagnostics repository/model if that is the smallest
  isolated persistence boundary;
- an additive Flyway migration if durable storage is required.

No change is justified in Trading Core, Market Intelligence, Broker Service,
Gateway, frontend, public subscription APIs, or runtime deployment configuration.

## Classification Rules

The implementation must apply explicit precedence and never infer a remote close
from `ON_COMPLETE` alone:

1. `APPLICATION_SHUTDOWN` when an application shutdown intent is explicitly
   known.
2. `EXPLICIT_UNSUBSCRIBE` when a local unsubscribe operation owns the close.
3. `LOCAL_DISPOSE` when a local disposal/cancellation owns the close.
4. `NETWORK_FAILURE` or `REMOTE_CLOSE` only when reliable client metadata or an
   error contract proves that classification.
5. `UNKNOWN_TRANSPORT_COMPLETION` for an exception-free completion without such
   proof.

A more specific known context must not be overwritten by a later generic
`doFinally` callback. Classification and sequence invalidation reason remain
separate fields.

## Persistence and Failure Boundary

Diagnostic persistence must be additive and idempotent for one physical
termination. It must use nullable fields for historical records where data is
not available, preserve existing evidence and sequence rows, and avoid raw
provider payloads, credentials, headers, or unbounded messages.

If diagnostic persistence fails:

- log a bounded diagnostic failure;
- continue sequence invalidation;
- continue in-memory cleanup;
- continue the existing recovery path;
- never report a trusted or recovered sequence because diagnostics failed.

The implementation plan must decide whether a separate connection termination
table or a sequence-linked context table is preferable after inspecting the
existing database conventions. That choice must not alter replay eligibility.

## Test Surface

Existing focused tests include:

- `KrakenDataStreamProviderTest` for terminal signals and cleanup ordering;
- `KrakenStreamLifecycleDiagnosticsTest` for exception/root-cause bounds;
- `OrderBookEvidenceCaptureServiceTest` for active sequence diagnostics and
  fail-closed invalidation;
- `OrderBookEvidencePersistenceTest` for durable sequence and replay behavior.

Required additions include:

- classification of `ON_COMPLETE` as unknown without close evidence;
- classification precedence for explicit shutdown, unsubscribe, and local dispose;
- bounded close and exception metadata;
- specificity preservation against generic terminal callbacks;
- one durable record per physical termination;
- persistence round-trip with nullable historical fields;
- diagnostic persistence failure not blocking invalidation or recovery;
- regression tests proving invalidated evidence remains excluded and fresh
  snapshot recovery still requires a new sequence.

## Validation Requirements

The affected module is `market-data`, an independent Java 21 Spring Boot Maven
service using Reactor WebFlux, PostgreSQL/Flyway, H2 tests, JUnit, Mockito, and
JaCoCo.

Planned validation:

```text
./mvnw -q -Dtest=KrakenDataStreamProviderTest,KrakenStreamLifecycleDiagnosticsTest,OrderBookEvidenceCaptureServiceTest,OrderBookEvidencePersistenceTest test
./mvnw -q verify
git diff --check
```

Runtime validation must observe only a natural termination event after normal
deployment. No restart, forced disconnect, reconnect, unsubscribe, subscription
change, database mutation, or PT1H baseline is authorized by this Story.

If no natural event occurs, report:

```text
RUNTIME_TERMINATION_VALIDATION = PENDING_NATURAL_EVENT
```

## Stop Conditions

Stop and request a new architectural decision if implementation requires:

- treating `ON_COMPLETE` as proof of remote close;
- changing sequence invalidation, replay, or fresh-snapshot recovery semantics;
- rewriting or deleting canonical evidence;
- introducing a new messaging or storage platform;
- forcing a disconnect or reconnect to generate a test event;
- logging or persisting secrets or raw provider payloads;
- changing public subscription API semantics.

## Recommendation

```text
ARCHITECTURE_DECISION: MARKET-DATA-OWNED-DURABLE-TRANSPORT-DIAGNOSTICS
CLASSIFICATION_POLICY: UNKNOWN-UNLESS-EXPLICITLY-PROVEN
TRUST_POLICY: PRESERVE-FAIL-CLOSED-SEQUENCE-INVALIDATION
PERSISTENCE_FAILURE_POLICY: BEST-EFFORT-DIAGNOSTICS-ONLY
IMPLEMENTATION_SCOPE: MARKET-DATA-ONLY
DATABASE_MIGRATION: REQUIRED-ONLY-IF-DURABLE-CONTEXT-CANNOT-BE-ADDITIVE-OTHERWISE
```

Implementation is ready to be planned after explicit human approval of this
Repository Analysis.
