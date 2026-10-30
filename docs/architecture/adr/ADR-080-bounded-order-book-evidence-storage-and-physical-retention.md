# ADR-080 - Bounded Order-Book Evidence Storage and Physical Retention Architecture

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-30 |
| **Related ADRs** | ADR-067, ADR-069, ADR-079 |
| **Decision Type** | Market Data evidence lifecycle and storage |

---

## 1. Context

Order-book evidence is canonical for deterministic reconstruction, but event-level
capture can grow by several gigabytes per day. ADR-067 establishes lifecycle-aware
retention and explicit dependency release, while the first implementation operates
at whole-sequence granularity. A long-lived sequence may contain trusted checkpoints,
but the old prefix cannot become independently governable.

## 2. Decision

Raw evidence retention is bounded at the level of explicit evidence segments.

```text
RAW_EVIDENCE_RETENTION_IS_BOUNDED = YES
RETENTION_GRANULARITY = SEGMENT
CHECKPOINT_CAN_ESTABLISH_NEW_REPLAY_BOUNDARY = YES
OLD_PREFIX_CAN_BECOME_RETIREABLE = YES
DEPENDENCY_RELEASE_REQUIRED = YES
ACTIVE_HOLD_BLOCKS_RETIREMENT = YES
UNTRUSTED_ARTIFACT_BLOCKS_RETIREMENT = YES
UNKNOWN_LEGACY_EVIDENCE_FAILS_CLOSED = YES
DISK_PRESSURE_CANNOT_BYPASS_GOVERNANCE = YES
LOGICAL_SEGMENT_DELETION_AUTHORIZED = YES
PHYSICAL_PARTITION_RETIREMENT_FUTURE_PHASE = YES
AUTOMATIC_DESTRUCTIVE_RETIREMENT_DEFAULT = DISABLED
```

A segment is a durable contiguous range of one provider, market, depth, and
sequence. It is opened for capture, closed only by sequence lifecycle or a trusted
successor boundary, and assessed independently from deletion.

## 3. Replay Boundaries

A trusted checkpoint can close the preceding raw prefix when continuity and
provenance are valid. The checkpoint belongs to the successor segment boundary;
the preceding segment ends immediately before its ordinal.

Kraken provider chronology remains distinct from cTrader local-observation
chronology. A cTrader local boundary can support local deterministic replay but does
not establish provider-time authority, provider sequence authority, or a complete
provider snapshot claim.

Missing continuity, generation crossover, invalidation, or unverifiable provenance
blocks segment eligibility.

## 4. Dependencies and Release

Existing protected/released dependency semantics remain authoritative. Dependencies
may target an artifact, sequence, or segment. A release is valid only when the
consumer has a durable successor result.

Market Intelligence releases a replay-artifact dependency only after the durable
feature snapshot exists and every configured outcome horizon has a persisted terminal
result. `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE` are terminal; `PENDING` is not.

Trading Core has no current production Order Book dependency creator, so no release
transition is invented for it. Its existing client remains available for a future
consumer workflow.

Release is idempotent and retains consumer identity, reason, and release timestamp.

## 5. Legacy Evidence

Existing evidence predating segment metadata is backfilled in bounded, resumable,
idempotent batches. It is classified `LEGACY_UNKNOWN` unless sufficient provenance
and boundary evidence exists. Unknown legacy segments remain protected and are never
made eligible by age alone.

## 6. Eligibility

A segment is eligible only when all of the following hold:

```text
segment is closed
AND a trusted successor boundary exists
AND classification is known
AND all related dependencies are released
AND a released dependency exists
AND no active hold exists
AND no untrusted artifact overlaps the segment
```

Retention preview reports the blocker categories. Explicitly eligible logical
segments may be deleted through the existing destructive-retention control path.
Destructive retirement remains disabled by default. This decision does not
authorize table rewrites or partition migration.

## 7. Physical Retention

Logical segment deletion is the currently authorized bounded-retention operation.
It deletes only the evidence rows covered by an eligible segment, preserves the
segment metadata as `RETIRED`, and supports bounded continuation through the
`RETIRING` state.

Future physical reclamation should map governed logical segments to physical
partitions or ranges. The preferred sequence is:

```text
eligible logical segment
    -> physical partition eligibility
    -> detach
    -> drop
    -> filesystem space reclaimed
```

The domain eligibility decision precedes any physical operation. Partition names and
PostgreSQL details must not become part of the domain contract.

## 8. Storage Pressure

Storage pressure remains an operational safety signal. It may pause optional capture
and emit degraded health, but it must not release dependencies, bypass holds, or
retire evidence implicitly. Destructive retention remains disabled by default.
