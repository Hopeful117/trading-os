# Repository Analysis - Story 0124

## Status

Repository Analysis completed. Implementation is not authorized until this
analysis and the subsequent Implementation Plan receive explicit human
approval.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  runtime behavior.
- `ADR-063` governs Order Book evidence ownership, sequence identity, trust,
  fail-closed recovery, and strict replay.
- `ADR-064` governs the economic boundary and requires one valid trusted Order
  Book sequence for economic replay.
- Story `0121` remains blocked with
  `CHECKSUM_SPECIFICATION_NOT_ESTABLISHED`. This Story must not implement a
  Kraken Futures checksum algorithm.
- Story `0122` established configuration-backed startup restoration and
  intentionally excluded unexpected-stream automatic recovery. Story `0124`
  explicitly brings bounded recovery and stale replay protection into scope.
- Story `0123` remains an investigation artifact and must not be broadened or
  modified by this Story.
- DevLog was queried and returned `429 insufficient_quota`; no conclusion in
  this analysis relies on unavailable DevLog context.
- Obsidian was not consulted because the current repository and accepted ADRs
  provide the required authoritative context.

## Git State

- Branch: `investigation/0123-forward-fee-schedule-runtime-activation`
- HEAD: `47dc921b16b99fb34841d0c880b9f665197a76b4`
- `origin/main`: same commit as HEAD at the last known local reference.
- `git fetch origin`: the current bounded fetch attempt timed out; no local
  repository changes were made by the attempt.
- Pre-existing modified files:
  - `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenFeeScheduleCapability.java`
  - `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenFeeScheduleCapabilityTest.java`
- Pre-existing untracked Story artifacts were preserved:
  - Story `0121` directory;
  - Story `0123` directory.
- Story `0124` currently contains only its Story and this Repository Analysis.
- No application code, migration, runtime database, container, or historical
  evidence was modified during this analysis.

## Current Order Book Architecture

The relevant runtime path is:

```text
OrderBookDesiredSubscriptionRestorer
    -> MarketSubscriptionServiceImpl
    -> KrakenDataStreamProvider
    -> KrakenStreamConnector
    -> Kraken Futures WebSocket
    -> OrderBookEvidenceCaptureService
    -> OrderBookEvidenceRepository
    -> historical_order_book_sequence/evidence
```

`OrderBookDesiredSubscriptionRestorer` restores the configured bounded
subscription once at `ApplicationReadyEvent`. It intentionally does not run a
periodic retry loop.

`MarketSubscriptionServiceImpl` keeps runtime subscriber counts in memory. A
subscription is started when its count changes from zero to one. The service
does not know whether the provider WebSocket later terminates unexpectedly.

`KrakenDataStreamProvider` owns the Futures connection, active subscriptions,
provider message dispatch, runtime state cleanup, and lifecycle diagnostics.

`OrderBookEvidenceCaptureService` owns the in-memory active sequence for each
market/depth key. It creates a new `sequenceId` when a valid snapshot is
accepted, rejects updates without an active sequence, and invalidates the
sequence on continuity or persistence failure.

`OrderBookEvidenceRepository` persists immutable evidence and sequence status.
The current schema supports only `ACTIVE` and `INVALIDATED` sequence states.

`OrderBookReplayService` delegates trusted evidence selection to the repository,
then reconstructs one selected sequence and returns `AVAILABLE`, `INVALID`, or
`UNAVAILABLE`.

## Confirmed Lifecycle Behavior

`KrakenDataStreamProvider.connect(Product)` creates the connection subscription
and handles inbound messages. Its `doFinally` callback:

- emits `STREAM_TERMINATING` diagnostics;
- clears live Order Book and trade state;
- invalidates the active evidence sequence through
  `OrderBookEvidenceCaptureService`;
- clears the WebSocket session and connection subscription references;
- emits `STREAM_CLEANUP_COMPLETED`.

The callback does not initiate a new connection or resubscribe active streams.
The active subscription maps remain populated, so a later explicit `connect`
can replay them, but no automatic recovery path currently invokes that behavior.

The existing provider test `reconnectReplaysActiveSubscriptions` proves that an
explicit second `connect()` replays active subscriptions. It does not prove
automatic recovery after terminal completion.

## Confirmed Sequence Behavior

`OrderBookEvidenceCaptureService.acceptSnapshot`:

1. validates that the delta is a snapshot and has no unvalidated checksum;
2. creates a random `sequenceId`;
3. persists the snapshot through `repository.append`;
4. stores the sequence in its in-memory map.

It does not invalidate any other persisted `ACTIVE` sequence for the same
market/depth before or after the new snapshot is persisted.

`OrderBookEvidenceCaptureService.invalidate` removes the in-memory sequence only
when repository invalidation succeeds. If invalidation persistence fails, the
service enters `durabilityCompromised` and fails closed.

`KrakenDataStreamProvider.clearRealtimeStates` calls `invalidate` for every
configured Order Book depth when a product stream terminates. This correctly
protects the current in-memory sequence, but it does not repair older persisted
sequences that were already left `ACTIVE` by an earlier sequence start.

## Confirmed Replay Defect

`OrderBookEvidenceRepository.findTrusted` currently requires:

```sql
evidence.trusted = true
AND sequence.status = 'ACTIVE'
AND evidence.known_at <= :cutoff
```

It does not require that the sequence is the current valid sequence for the
market/depth, that the sequence has not been superseded, or that the evidence is
fresh according to an explicit consumer policy.

The runtime investigation found exactly this failure mode:

- overnight sequence `8de02744...` accumulated evidence and was later
  invalidated;
- older sequence `3ad4352d...` remained `ACTIVE` but was stale;
- the current valid replay population was therefore empty, while the canonical
  `ACTIVE` query could still expose old evidence.

This is a replay-integrity defect, not merely a freshness warning. A stale
sequence must not remain eligible solely because its status was never closed.

## Schema Assessment

The existing `V6__historical_order_book_evidence.sql` schema is sufficient to
implement the minimum lifecycle correction without introducing a new storage
platform:

- sequence identity is durable;
- market and depth are indexed;
- status and invalidation metadata are durable;
- evidence is immutable and fingerprinted;
- replay can filter by sequence status and `knownAt`.

A migration may be unnecessary if the implementation invalidates superseded
active sequences using the existing status model and makes the transition
atomic with snapshot acceptance. A migration becomes necessary only if the
approved design introduces an explicit current-sequence marker, terminal reason
taxonomy, or a new lifecycle state. That decision belongs in the
Implementation Plan, not this analysis.

## Architectural Interpretation

The repository already follows the fail-closed direction required by ADR-063:

- no delta is accepted before a snapshot;
- provider sequence gaps invalidate the active sequence;
- persistence failures invalidate or quarantine capture;
- an invalidated sequence is excluded by the current repository query;
- in-memory state is cleared at stream termination.

The missing behavior is recovery and durable uniqueness of the replay-eligible
sequence:

```text
terminal stream
    -> invalidate current sequence
    -> clear runtime state
    -> establish a fresh provider connection
    -> subscribe again
    -> accept a fresh snapshot
    -> start a new sequence
    -> accept deltas only in that sequence
```

The recovery path must not resurrect an invalidated sequence or append new
deltas to it. It must also avoid creating multiple durable `ACTIVE` sequences
for the same bounded market/depth scope.

## Recommended Implementation Boundary

The likely implementation surface is limited to `market-data`:

- `KrakenDataStreamProvider` for terminal recovery and subscription replay;
- `OrderBookEvidenceCaptureService` and/or
  `OrderBookEvidenceRepository` for superseded active-sequence handling;
- `OrderBookReplayService` or repository selection for explicit stale/invalid
  replay results;
- `KrakenStreamLifecycleDiagnostics` for recovery lifecycle events;
- focused Market Data tests.

No changes are currently justified in Trading Core, Market Intelligence, Broker
Service, fees, funding, FeatureSnapshots, outcomes, Gateway, or frontend code.

The implementation must preserve the existing bounded scope:

```text
provider: KRAKEN
market: BTC/USD
product: LINEAR_PERPETUAL
provider symbol: PF_XBTUSD
```

## Recovery Policy Questions for the Implementation Plan

The Story authorizes recovery, but the Implementation Plan must make these
operational semantics explicit before code changes:

- whether recovery is immediate or uses bounded backoff;
- how repeated terminal failures avoid an unbounded reconnect loop;
- whether manual unsubscribe suppresses recovery for that runtime subscription;
- how simultaneous terminal signals are deduplicated;
- which diagnostics distinguish a recovery attempt, success, and failure;
- whether active-sequence invalidation and fresh snapshot acceptance require one
  atomic repository operation.

These are implementation-policy questions, not reasons to broaden the domain or
introduce another service.

## Test Surface

Existing focused coverage includes:

- `KrakenDataStreamProviderTest` for connection lifecycle, explicit replay of
  active subscriptions, termination diagnostics, and cleanup;
- `OrderBookEvidenceCaptureServiceTest` for snapshots, sequence gaps,
  persistence failures, invalidation failures, and checksum conservatism;
- `OrderBookReplayServiceTest` for cutoff filtering, unavailable replay,
  non-contiguous replay, and unexpected snapshots;
- `OrderBookEvidencePersistenceTest` for invalidated-sequence exclusion and
  fingerprint idempotency;
- `OrderBookDesiredSubscriptionRestorerTest` for bounded startup restoration.

Missing focused coverage includes:

- automatic recovery after `ON_COMPLETE`, `ON_ERROR`, and cancellation;
- recovery deduplication and repeated terminal events;
- fresh snapshot/new sequence after recovery;
- invalidation or exclusion of superseded persisted active sequences;
- strict replay behavior when only stale active evidence exists;
- recovery failure without fabricated active state or evidence.

## Validation Requirements

The affected module is `market-data`, an independent Spring Boot Maven service
using Java 21, Spring Boot 4.1.0, PostgreSQL/Flyway, H2 tests, Reactor WebFlux,
JUnit, Mockito, and JaCoCo.

The Implementation Plan should require at minimum:

- targeted unit tests for provider, capture, repository, and replay behavior;
- PostgreSQL-backed persistence tests for sequence state transitions;
- the independent Market Data Maven test/verification command;
- `git diff --check`;
- runtime verification that a terminal stream produces a fresh valid sequence
  without rewriting existing evidence.

SonarQube applicability should be checked for project key
`trading-os:market-data` during implementation quality validation.

## Risks and Stop Conditions

Stop and request a new architectural decision if implementation requires:

- treating the stale sequence as valid historical evidence;
- rewriting or deleting existing evidence;
- a new provider-specific checksum assumption;
- changing economic ownership or result semantics;
- a database-backed subscription-intent domain;
- unbounded reconnect loops or cross-service recovery orchestration;
- changing public subscription API semantics;
- a new storage or messaging platform.

## Recommendation

```text
ARCHITECTURE_DECISION: MARKET-DATA-OWNED-BOUNDED-SEQUENCE-RECOVERY
REPLAY_POLICY: EXCLUDE-SUPERSEDED-OR-STALE-ACTIVE-SEQUENCES
CHECKSUM: PRESERVE-CURRENT-CONSERVATIVE-BEHAVIOR
HISTORICAL-EVIDENCE-REWRITE: NO
IMPLEMENTATION_SCOPE: MARKET-DATA-ONLY
DATABASE_MIGRATION: TO-BE-DETERMINED-BY-ATOMIC-TRANSITION-DESIGN
```

Implementation Planning must not begin until this Repository Analysis receives
explicit human approval.
