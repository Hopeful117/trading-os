# Story 0138 - Repository Analysis

## Status

Completed for human approval before Implementation Planning.

## Scope Reviewed

This analysis covers the approved Story 0138, ADR-067, the existing Order Book
capture/replay implementation, Market Data Flyway/JDBC conventions, and current
research identity boundaries. No production code or research data was modified.

DevLog was queried for prior project context but was unavailable because the
configured provider reported exhausted quota. The current repository and accepted
ADRs remain authoritative for the current physical state.

## Current Repository State

- Branch: `main`.
- Existing unrelated untracked reports are preserved.
- ADR-067 is accepted and defines lifecycle-aware retention with promoted replay
  artifacts as the reproducibility boundary.
- Story 0138 is approved and is the governing implementation scope.
- The existing Order Book evidence remains in PostgreSQL and was not changed.
- No retention, promotion, dependency, or cleanup implementation exists.

## Architectural Context

ADR-063 and ADR-065 establish the following authority boundaries:

```text
Market Data
    acquire, normalize, validate, persist, replay, and retire Order Book evidence

Market Intelligence
    create and consume research facts that reference replay provenance

Trading Core
    consume historical execution replay dependencies where applicable
```

ADR-066 requires frozen research populations to bind explicit immutable semantic
identities. ADR-067 extends that rule to the Order Book replay evidence supporting
those facts.

The implementation must not move retention decisions into Market Intelligence or
Trading Core. Those modules may create or release dependency contracts, but Market
Data remains authoritative for evidence lifecycle and physical retirement.

## Existing Order Book Implementation

Relevant files:

- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceCaptureService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/OrderBookEvidence.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/OrderBookReplayResult.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/OrderBookSequenceStatus.java`
- `market-data/src/main/resources/db/migration/V6__historical_order_book_evidence.sql`
- `market-data/src/main/resources/db/migration/V10__sealed_order_book_epochs.sql`

The capture service currently:

- persists provider snapshots and deltas as immutable fingerprinted evidence;
- creates and tracks active sequences in memory;
- marks sequences sealed or invalidated through the repository;
- writes derived checkpoints into the same evidence table with checkpoint origin;
- fails closed when persistence or lifecycle updates cannot be confirmed;
- has no promotion or dependency lifecycle.

The replay service currently:

- queries trusted evidence through a strict cutoff;
- selects a single eligible sequence;
- selects a snapshot or checkpoint as its starting point;
- applies contiguous deltas from that sequence;
- returns explicit unavailable or invalid results;
- produces a replay fingerprint from source evidence fingerprints.

The replay fingerprint and provenance fields provide a useful basis for promotion,
but the current result is not persisted as an immutable research artifact.

## Existing Database Model

`V6__historical_order_book_evidence.sql` defines:

- `historical_order_book_sequence`;
- `historical_order_book_evidence`;
- fingerprint uniqueness;
- replay and captured-time indexes;
- sequence status indexing.

`V10__sealed_order_book_epochs.sql` adds `SEALED` lifecycle support and conservative
transport-backed historical salvage. The current status model is only
`ACTIVE`, `SEALED`, and `INVALIDATED`; it does not represent retention protection,
promotion, release, or hold state.

There are no tables for:

- promoted replay artifacts;
- semantic replay dependencies;
- dependency release events;
- retention holds;
- retirement attempts or audit records;
- raw evidence retirement state.

The implementation therefore requires new versioned Flyway migrations. Existing
evidence rows must remain valid under the new schema without assuming historical
dependency metadata exists.

## Required Data Boundaries

The implementation should keep the following concepts separate:

1. **Replay artifact**: immutable result and source provenance for a strict cutoff.
2. **Dependency**: consumer-owned semantic reference to the evidence or artifact.
3. **Retention hold**: explicit blocker independent of consumer lifecycle.
4. **Retirement plan/audit**: selected evidence range and lifecycle operation.
5. **Physical evidence**: canonical snapshots, deltas, and derived checkpoints.

A sequence status must not be overloaded with retention state. A sealed sequence can
remain protected, promoted, held, released, or eligible for retirement independently
of its continuity lifecycle.

## Promotion Requirements

Promotion should be exposed through an application service over the existing replay
service rather than reimplementing reconstruction. It must:

- accept a completed replay result and its semantic request identity;
- reject unavailable or invalid results;
- preserve market, instrument/provider provenance, depth, cutoff, sequence, source
  range, trust, normalization version, and fingerprints;
- validate that the replay result has no look-ahead evidence;
- create the same artifact once for repeated semantic requests;
- detect conflicting content for an existing semantic identity;
- remain immutable after creation.

The artifact identity should be content-addressed or fingerprinted from the replay
request and source provenance. A generated database UUID may remain a persistence
identifier but cannot be the portable research identity.

## Dependency Requirements

Dependencies need a stable owner/type/reference contract that can represent at least:

```text
consumer type
consumer semantic identity
replay artifact or source evidence identity
state: PROTECTED | PROMOTED | RELEASED | HOLD
created/released timestamps
reason and provenance
```

Unknown or missing records must protect evidence. A cleanup query must not infer that
no dependency exists merely because no dependency row was found.

The current repository does not yet expose a complete cross-service dependency
contract. Implementation planning must decide whether the first V1 dependency
registry is owned entirely by Market Data or whether Market Intelligence and Trading
Core persist release references through internal application ports. This is a
contract decision, not a reason to use direct cross-service database access.

## Retirement Requirements

Retirement must be a separate application boundary from promotion and replay. It
must initially support:

- dry-run selection;
- exact evidence range reporting;
- protection by active sequence status;
- protection by unresolved dependency or hold;
- conservative handling of unknown metadata;
- an audit record for each attempt;
- atomic and resumable execution;
- idempotent repeated execution;
- explicit enablement before destructive mutation.

The first implementation should not physically delete the current large evidence
population. It should prove the lifecycle on newly promoted/test-controlled data and
only then define a separately approved migration for historical rows.

Physical deletion also has PostgreSQL operational implications: indexes, long-running
transactions, vacuum pressure, and table bloat must be measured. Partitioning or
compaction is not implied by this Story and requires a later design if needed.

## Storage-Pressure Integration

`OrderBookEvidenceCaptureService` already tracks a local durability-compromised
state and fails closed after persistence errors. Story 0138 should extend the
observability boundary rather than bypass it.

Required signals include:

- available filesystem/database headroom;
- last successful evidence persistence;
- current active sequence;
- capture and persistence failure counts;
- degraded/quarantined capture state;
- promotion and cleanup progress.

The pressure gate must stop or quarantine canonical capture when evidence cannot be
durably acknowledged. It must not automatically delete evidence or downgrade strict
replay semantics.

## Risks and Gaps

### Confirmed Gaps

- No promoted replay artifact persistence exists.
- No dependency registry or release contract exists.
- No retention hold model exists.
- No retirement audit or dry-run cleanup exists.
- No physical evidence retirement state exists.
- Existing replay result persistence is absent.
- Existing sequence status is not a retention state.

### Important Design Risks

- Deleting evidence without a conservative dependency backfill could invalidate
  frozen research.
- Treating a checkpoint as a promotion artifact would lose explicit replay request
  and research provenance.
- Direct cross-service database access would violate service ownership boundaries.
- A cleanup query that interprets missing dependency rows as released would fail open.
- Large PostgreSQL deletes can create table/index bloat and affect capture latency.
- The current replay repository selects eligible sequences conservatively; promotion
  must preserve that behavior rather than broadening sequence selection.

## Proposed Validation Surface

Focused tests should cover:

- promotion of a valid replay;
- rejection of invalid, unavailable, incomplete, cross-sequence, and look-ahead
  results;
- deterministic artifact fingerprints;
- idempotent duplicate promotion;
- conflicting promotion identity;
- dependency state transitions;
- unknown dependency and hold protection;
- active/sealed/invalidated sequence retention behavior;
- dry-run retirement selection;
- retirement audit and idempotency;
- restart/resume behavior;
- storage-pressure capture quarantine;
- PostgreSQL migration compatibility with existing evidence.

Existing tests to extend or use as patterns include:

- `OrderBookEvidenceCaptureServiceTest`;
- `OrderBookReplayServiceTest`;
- `OrderBookEvidenceRepositoryTest`;
- `ResearchReadinessServiceTest`;
- Market Data PostgreSQL migration/integration tests.

## Recommended Implementation Sequence

This sequence is an implementation-planning input, not an approved implementation
plan:

1. Define the replay artifact and dependency semantic identities.
2. Add immutable promotion persistence and repository contracts.
3. Add dependency and retention-hold lifecycle persistence.
4. Add dry-run retirement selection and audit records with mutation disabled.
5. Add controlled, idempotent retirement for explicitly selected test/protected
   ranges only.
6. Add storage-pressure metrics and capture quarantine behavior.
7. Validate runtime behavior and PostgreSQL operational effects.
8. Prepare a separate historical dependency backfill and retirement proposal.

## Decision and Recommendation

The repository can support Story 0138 without a new service or storage platform,
but the implementation requires new Market Data persistence contracts and a clear
cross-service dependency boundary. The safest first increment is promotion plus
dependency protection and dry-run retirement. Destructive historical cleanup should
remain disabled until a later human-approved migration decision.

Repository Analysis recommendation: **READY FOR IMPLEMENTATION PLANNING**, subject to
human approval of this analysis and explicit agreement on the ownership of the V1
dependency registry.
