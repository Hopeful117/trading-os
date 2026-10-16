# Story 0112A - Repository Analysis

## Status

Completed for human approval before Implementation Planning.

## Scope Reviewed

This analysis covers the approved Story 0112A, ADR-063, the current Market Data
Order Book implementation, its Kraken WebSocket adapter, existing PostgreSQL and
Flyway conventions, and the focused Order Book tests. No production code was
modified during this analysis.

DevLog was queried for prior project context but was unavailable because the
configured provider reported exhausted quota. The current repository and accepted
ADR-063 remain the authoritative sources for this analysis.

## Current Repository State

- Branch: `main`.
- Existing unrelated worktree changes are preserved:
  - `docker-compose.yml` contains the approved local Story 0111 environment
    mappings.
  - `docs/architecture/adr/ADR-063.md` is the accepted ADR proposal.
  - `research/ml/src/research_dataset/__pycache__/` remains untracked and
    untouched.
- Story 0112A exists at
  `docs/architecture/stories/0112A-durable-order-book-evidence-acquisition/story.md`.
- The current Story status is `Draft`; human approval of this Repository Analysis
  is the next workflow gate.

## Architectural Context

ADR-063 assigns the following responsibilities:

```text
Market Data
    acquire provider Order Book messages
    normalize and validate trust
    persist immutable evidence
    reconstruct and replay valid sequences

Market Intelligence
    consume replayed OrderBookContext later
    calculate deterministic liquidity features in Story 0112B
```

The current Story must not add liquidity features, modify
`feature-projection-v1`, expose a public API, or introduce a new infrastructure
platform.

## Existing Order Book Flow

The current runtime flow is:

```text
Kraken WebSocket
    -> KrakenDataStreamProvider
    -> KrakenOrderBookMapper
    -> OrderBookDelta
    -> OrderBookStateService
    -> OrderBookSnapshot
    -> OrderBookEventPublisher
    -> latest in-memory snapshot / reactive stream
```

Relevant facts:

- `OrderBookDelta` contains market identity, provider, symbol, depth, snapshot or
  update type, levels, `occurredAt`, and an optional checksum.
- `OrderBookSnapshot` contains reconstructed levels plus derived spread, volume,
  imbalance, and `occurredAt`.
- `OrderBookStateService` keeps one in-memory `TreeMap` state per market/depth,
  applies zero-quantity deletions, and orders bids descending and asks ascending.
- `OrderBookEventPublisher` stores only the latest snapshot per market/depth and
  publishes updates through an in-memory Reactor sink.
- `clearRealtimeStates` clears the in-memory state when a WebSocket connection
  terminates.
- No Order Book evidence entity, repository, Flyway migration, fingerprint, replay
  service, checkpoint model, or durable acquisition writer exists.

The existing `OrderBookSnapshot` must not become the canonical historical record
without separating provider evidence from derived metrics. In particular, the
current spread, volume, and imbalance fields are derived operational values and
are outside Story 0112A's feature scope.

## Provider Adapter Findings

### Spot

`KrakenDataStreamProvider.handleOrderBookMessage` maps Spot `book` messages and
passes them to the in-memory state service. The generic DTO contains `symbol`,
bids, asks, checksum, and timestamp. The current mapper preserves the checksum
but does not validate it.

### Futures

The Futures path handles `book` and `book_snapshot` feeds and maps `product_id`
to the subscribed Market Data market. The current bounded market uses
`PF_XBTUSD`. Futures levels are parsed from either object fields or positional
arrays, and the feed timestamp is converted to `Instant`.

The current DTO and handler do not preserve an explicit provider sequence number.
The implementation must not invent one. Story 0112A should preserve provider
sequence metadata if the actual feed exposes it and otherwise record the
limitation while relying on connection boundaries, checksum validation, and local
accepted ordering.

The current adapter stores the checksum value but has no checksum algorithm or
book-content validation. This is a required implementation gap, not an existing
guarantee.

### Subscription Scope

`MarketSubscriptionServiceImpl` currently accepts only depths `10` and `25`.
Therefore ADR-063's initial depth-25 policy is compatible with the current
subscription boundary and does not require expanding provider depth support.

The existing subscription registry is in-memory and supports Futures markets by
provider symbol. It can supply the bounded `PF_XBTUSD` market but is not a durable
capture configuration by itself.

## Existing Persistence Conventions

Market Data's historical OHLC implementation provides the closest repository
pattern:

- domain record with constructor validation;
- immutable JPA entity using `@Immutable`;
- Flyway-owned PostgreSQL schema;
- deterministic normalization version and content fingerprint;
- `INSERT ... ON CONFLICT (observation_fingerprint) DO NOTHING`;
- separate acquisition time and `knownAt`;
- repository-first strict reads with explicit cutoff predicates;
- append-only correction behavior rather than silent overwrite.

Relevant files include:

- `HistoricalOhlcEvidence.java`;
- `HistoricalOhlcEvidenceWriter.java`;
- `JpaHistoricalOhlcEvidenceEntity.java`;
- `JpaHistoricalOhlcEvidenceRepository.java`;
- `HistoricalOhlcEvidenceInsertRepositoryImpl.java`;
- `V5__historical_ohlc_evidence.sql`.

These patterns should be reused conceptually, not by placing Order Book events in
the OHLC table. Order Book evidence has sequence and reconstruction semantics that
require a separate physical model.

## Required Implementation Boundaries

The implementation will need distinct boundaries for:

1. Provider-neutral canonical evidence records.
2. Provider adapter mapping and checksum validation.
3. Sequence lifecycle and trust invalidation.
4. Append-only persistence and fingerprint idempotency.
5. Derived checkpoint persistence.
6. Replay selection at `T0`.
7. Operational metrics and failure diagnostics.

The current `OrderBookStateService` can remain the in-memory reconstruction engine
only if its lifecycle is extended or wrapped so that persistence and trust state
are applied before a snapshot becomes eligible for research. A direct persistence
subscriber to the latest reactive stream would be insufficient because the stream
is best-effort and does not represent durable evidence or rejected messages.

## Temporal and Replay Requirements

The canonical model must carry:

```text
occurredAt = provider/source event time
knownAt    = trusted acquisition/acceptance time
```

Replay must select one valid reconstruction sequence and apply only evidence with
`knownAt <= T0`. It must return explicit invalid or unavailable state when the
sequence cannot be established. It must not use the current in-memory snapshot,
current provider REST state, future evidence, or deltas after a known gap.

A reconnect or application restart must clear untrusted in-memory state and begin
from a new provider snapshot. A checksum failure, malformed snapshot, detected
out-of-order update, sequence gap, or persistence failure must prevent continued
trusted replay for the current sequence.

## Data Model Implications

The implementation should define, at minimum:

- immutable evidence identity;
- `SNAPSHOT` and `DELTA` evidence types;
- `sequenceId` and local sequence ordering;
- optional provider sequence provenance;
- market/provider/provider-symbol provenance;
- depth and canonical bid/ask levels;
- `occurredAt` and `knownAt`;
- checksum value and algorithm/trust metadata;
- `order-book-normalization-v1`;
- deterministic content fingerprint;
- explicit accepted/rejected or trusted/untrusted semantics;
- checkpoint provenance to its source sequence and evidence range.

Canonical levels must use `BigDecimal` values and deterministic ordering. Raw
Kraken payloads must remain inside the provider adapter and are not required in
the canonical V1 research contract.

## Risks and Gaps

### Confirmed Gaps

- No durable Order Book persistence exists.
- No replay service or cutoff query exists.
- No checksum validation exists despite checksum fields being transported.
- No sequence identity or gap detection exists.
- No durable checkpoint model exists.
- In-memory state is cleared on connection termination but no durable sequence
  invalidation is recorded.
- Current tests cover mapping, in-memory ordering, metrics, and live fan-out only.

### Provider Uncertainties

- The current Futures DTO does not contain a provider sequence field.
- The actual Futures feed's sequence and checksum semantics must be confirmed in
  adapter tests against representative payloads before implementation is
  finalized.
- Network-level gaps cannot be claimed detectable when the provider does not
  expose a sequence or equivalent continuity marker.

### Operational Risks

- Event-level persistence volume is unknown and must be measured before expanding
  beyond one market at depth 25.
- Persistence back-pressure could cause the in-memory state and canonical evidence
  to diverge; the implementation must fail closed rather than acknowledge
  unpersisted trusted evidence.
- Reconnect handling must not merge pre-gap and post-reconnect deltas.
- Checkpoints must remain clearly derived and must not replace canonical evidence.

## Proposed Validation Surface

The implementation should add focused tests for:

- provider-neutral normalization and canonical ordering;
- `occurredAt`/`knownAt` validation;
- checksum acceptance and rejection;
- snapshot-established sequences;
- reconnect, restart, invalidation, and fresh sequence creation;
- out-of-order and gap handling where provider metadata permits detection;
- idempotent inserts and immutable conflicts;
- checkpoint provenance;
- replay at cutoff boundaries, including exclusion of `knownAt > T0` evidence;
- unavailable replay for incomplete or invalid sequences;
- PostgreSQL append-only and uniqueness constraints.

Existing tests to extend or use as patterns:

- `KrakenOrderBookMapperTest`;
- `KrakenDataStreamProviderTest`;
- `KrakenDataProviderDispatchTest`;
- `OrderBookStateServiceTest`;
- `OrderBookEventPublisherTest`;
- `HistoricalOhlcEvidencePersistenceTest`;
- `HistoricalOhlcEvidenceWriterTest`.

The Market Data Maven module has JaCoCo verification with an 80% line coverage
threshold. The focused implementation should preserve that module quality gate.

## Recommended Implementation Sequence

The following is an implementation-planning input, not an approved plan:

1. Confirm representative Kraken Futures snapshot/update payloads and exact trust
   metadata available from the feed.
2. Define and validate provider-neutral evidence domain records and fingerprints.
3. Add Flyway schema and immutable persistence boundaries.
4. Add sequence/trust lifecycle around provider message handling.
5. Add checkpoint persistence and deterministic replay queries.
6. Add bounded configuration and observability for `PF_XBTUSD` depth 25.
7. Add focused unit, PostgreSQL integration, reconnect, and replay tests.
8. Validate local runtime persistence without enabling the full market catalogue.

No implementation plan is created by this Repository Analysis.

## Decision and Recommendation

The current repository can support Story 0112A without a new service or storage
platform. PostgreSQL and the existing Market Data Flyway/JPA conventions are
sufficient for the bounded pilot. The Story is implementable within Market Data,
but checksum trust and provider sequence availability must be resolved explicitly
in the implementation rather than assumed from the current adapter.

Repository Analysis recommendation: **READY FOR IMPLEMENTATION PLANNING**,
subject to human approval of this analysis and confirmation of the provider feed
payload semantics during planning.
