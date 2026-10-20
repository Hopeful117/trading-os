# Order Book Evidence Retention and Replay Storage Investigation

**Date:** 2026-10-20  
**Status:** RETENTION_ARCHITECTURE_READY_WITH_OPEN_DECISIONS  
**Scope:** Market Data Order Book evidence capture, replay, research reproducibility, and storage lifecycle

## Executive Summary

The current Order Book evidence pipeline is deterministic and fail-closed for replay, but raw evidence has no bounded retention policy. A single Kraken `BTC/USD` linear perpetual market at depth 25 produced approximately 36.2 million evidence rows and 20.6 GB of PostgreSQL data. Observed growth was approximately 0.25-0.30 GB/hour, or 6-7 GB/day.

The immediate storage incident was caused by accumulated Docker artifacts together with growing research data. Docker build cache and unused images were reclaimed after explicit operator authorization. No containers, named volumes, PostgreSQL rows, schemas, or application configuration were removed or changed. The filesystem now has approximately 94 GB free and PostgreSQL accepts connections.

The durable solution is not a fixed age-based delete. The preferred V1 is lifecycle-aware retention: protect active sequences and research dependencies, promote immutable replay artifacts at research boundaries, and retire raw evidence only after all replay and reproducibility dependencies have been explicitly released.

## Current Findings

### Acquisition and Persistence

The acquisition path is:

```text
Kraken WebSocket
  -> KrakenDataStreamProvider
  -> normalized OrderBookDelta
  -> in-memory order-book reconstruction
  -> OrderBookEvidenceCaptureService
  -> PostgreSQL sequence/evidence persistence
  -> strict replay
```

The evidence model includes snapshots, deltas, and checkpoints, with sequence identifiers, ordinals, provider sequence values, `occurredAt`, `knownAt`, fingerprints, normalization version, trust, and lifecycle state.

Invalid sequences are not replay-eligible. Sealed sequences remain replayable through their trusted boundary. The replay index is ordered by market, depth, sequence, known time, and sequence ordinal.

### Replay Correctness

Replay requires one trusted snapshot or checkpoint plus contiguous deltas through the requested cutoff. Evidence with `knownAt` after the cutoff is excluded. This prevents look-ahead leakage and preserves deterministic reconstruction.

The current consumers include Market Intelligence Order Book context/readiness and Trading Core historical execution replay. S1 readiness replay coverage remains 0%, and fee coverage remains 0% because the Market Intelligence fee parameters are unset.

### Retention Gap

There is no implemented retention, cleanup, compaction, partition-retention, or archival policy. The operational replay horizon is therefore unbounded in practice. The reproducibility horizon is also undefined.

A fixed time-to-live is unsafe until the project defines whether frozen research requires raw recomputation indefinitely or whether promoted replay artifacts are sufficient evidence for reproducibility.

## Storage Incident and Recovery

Initial condition:

```text
Filesystem: 449G total, 100% used, 0B free
Docker build cache: approximately 69.6G, 44.9G reclaimable
Docker images: approximately 51.2G, 18.0G reclaimable
Order Book evidence: approximately 36.2M rows, 20.6G
```

Authorized remediation:

```text
docker image prune --all --force
```

The cleanup reclaimed approximately 55.9 GB in total. No volume pruning or container pruning was performed. Active containers remained running, including `trading-postgres`, and the verification query `select 1` succeeded.

Final condition:

```text
Filesystem: 449G total, 332G used, 94G free, 78% used
Docker build cache: 0B
Docker images: 21 active images
PostgreSQL: accepting connections
```

Unused images may need to be rebuilt before future development or test runs.

## Preferred V1 Retention Architecture

Use lifecycle-aware retention with explicit dependency release:

1. Keep active and recovery-required sequences immutable and protected.
2. At a research or replay boundary, promote an immutable replay artifact containing the trusted starting point, contiguous evidence boundary, cutoff, normalization version, and provenance.
3. Record which research, FeatureSnapshot, execution replay, or diagnostic dependency is protected by that artifact.
4. Allow cleanup only when the sequence is non-active and every dependent artifact has been promoted or explicitly released.
5. Make retirement atomic and idempotent.
6. Fail closed when retention metadata is missing or inconsistent.

This design preserves deterministic S1 correctness without requiring indefinite raw delta retention. It also makes storage release an explicit domain operation rather than an implicit age-based side effect.

## Required Operational Controls

- Storage-pressure thresholds that stop or degrade capture before persistence becomes unreliable.
- Metrics for last successful evidence persistence, sequence health, replay readiness, and cleanup progress.
- Acquisition-health integration that distinguishes broker connectivity from local persistence failure.
- Replay failure when required evidence has been retired or is incomplete; never substitute a partial result silently.
- A documented storage budget and growth invariant for each capture profile.
- Tests for look-ahead exclusion, sequence invalidation, promotion, dependency protection, release, cleanup idempotency, and storage-pressure behavior.
- Runtime validation under sustained capture and replay workloads.

## Open Decisions

The following decisions require human approval before implementation:

- Are promoted replay artifacts sufficient for frozen research reproducibility?
- Must raw evidence remain available for indefinite recomputation?
- What event freezes or releases a research population?
- How long should invalidated sequences remain available for diagnostics?
- What replay provenance and normalization-version contract is required?
- Should fee coverage configuration be completed as part of the research readiness work?

## Recommended Architecture Decision Record

Create:

```text
ADR - Bounded Order Book Evidence Retention and Replay Promotion
```

The ADR should define the reproducibility contract, dependency model, promotion boundary, release semantics, invalidated-sequence policy, cleanup authorization, and fail-closed behavior.

## Recommended Story Slicing

1. Retention dependency and replay-promotion foundations.
2. Bounded cleanup and retirement implementation.
3. Storage-pressure observability and sustained validation.

Do not delete the existing approximately 36.2 million evidence rows until the ADR and retention contract are approved and a migration or cleanup procedure has been validated.

## Validation Limits

The investigation was limited by the original full filesystem. Exact physical table/index size breakdowns, replay-reason distribution, broad corruption scans, and sustained replay benchmarks were not completed before cleanup. They should be collected now that PostgreSQL is healthy and storage headroom exists.

## Relevant Artifacts

- `docs/architecture/adr/ADR-063.md`
- `docs/architecture/adr/ADR-065.md`
- `docs/architecture/stories/0111-continuous-research-data-lifecycle/story.md`
- `docs/architecture/stories/0112A-durable-order-book-evidence-acquisition/story.md`
- `docs/architecture/stories/0115-deterministic-market-execution-replay/story.md`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceCaptureService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- `market-data/src/main/resources/db/migration/V6__historical_order_book_evidence.sql`
- `market-data/src/main/resources/db/migration/V10__sealed_order_book_epochs.sql`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessService.java`
