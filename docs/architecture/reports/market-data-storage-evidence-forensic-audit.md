# Market Data Storage and Evidence Forensic Audit

**Date:** 2026-11-06  
**Status:** `AUDIT_COMPLETE_WITH_BLOCKERS`  
**Mode:** read-only operational inspection; no runtime, database, or capture mutation

## Objective

Determine why the current Market Data runtime consumes substantial storage while
the historical research export remains empty, classify the existing evidence, and
identify the smallest safe next decision. This audit does not implement cleanup,
retention, migration, reprojection, or new data capture.

## Executive Summary

The current state is not safe for further unbounded collection:

- The host filesystem is at 100% capacity with approximately 3.0 GB free.
- The `market_data` PostgreSQL volume is the dominant Trading OS data volume at
  approximately 85 GB.
- `historical_order_book_evidence` is estimated at approximately 120.4 million
  rows and approximately 85 GB including indexes. Exact row count was not run
  because the table is large and PostgreSQL statistics are stale.
- The Order Book capture gate is currently `false`; the separate Order Book
  acquisition/subscription gate is `true`. Historical OHLC capture remains
  enabled and is still accepting approximately 199-200 rows per market and
  interval on a 15-minute schedule.
- The current research export has 96 exclusions and zero eligible samples.
  The immediate blocker is not absence of all market data. Historical OHLC has
  populated `fetched_at`, `captured_at`, and `known_at`. The blocker is that the
  Market Intelligence context sections used by the feature snapshots do not
  contain the required `fetchedAt` provenance, and the snapshots were created
  after their evaluation cutoffs.
- Existing Order Book evidence cannot yet be classified as research-recoverable.
  Its schema contains the required temporal and trust fields, but no bounded
  historical replay against the current population was performed. The table
  must remain protected until dependency and replay eligibility are proven.

The correct next step is a human-approved storage-pressure response and a
targeted provenance repair design. More collection should not be treated as a
way to solve the empty dataset.

## Scope And Safety Boundary

The following operational actions were not performed:

- no service restart, deployment, or configuration change;
- no PostgreSQL write, migration, `VACUUM`, `REINDEX`, delete, or retention run;
- no Docker prune or volume cleanup;
- no LLM, broker, trade, or external write;
- no large-table `COUNT(*)`, min/max scan, `EXPLAIN ANALYZE`, or replay request;
- no change to pre-existing worktree modifications.

The worktree was already dirty before this report. Existing modified and
untracked files were preserved. This report is the only intentional audit
artifact added by this work.

## Measured Runtime State

### Host and Docker

```text
Filesystem:       449G total, 423G used, 3.0G free, 100%
Docker images:    13.66G total, 5.304G reclaimable
Docker containers: 238.1M total
Docker volumes:   96.72G total, 442.2M reclaimable
Build cache:      24.4G total, 16.25G reclaimable
```

The Docker build cache is a separate potential reclamation candidate, but no
cleanup was authorized or executed during this audit. The 85 GB PostgreSQL
volume is not safely reclaimable through generic Docker cleanup.

The active Trading OS services include Market Data, Market Intelligence,
Trading Core, Broker, Gateway, News, and their PostgreSQL dependencies. No
runtime mutation was required to establish this state.

### Database Allocation

Measured database sizes:

| Database | Approximate size |
| --- | ---: |
| `market_data` | 85 GB |
| `trading_os` | 2.613 GB |
| `market_intelligence` | 134 MB |
| `broker_service` | 8.9 MB |

Within `market_data`, `historical_order_book_evidence` is approximately 85 GB.
Its approximate relation estimate is 120,427,504 rows, but PostgreSQL reports
stale statistics (`n_live_tup=0`, with no recent analyze/autovacuum evidence).
This estimate must not be presented as an exact count.

The Order Book table is index-heavy:

| Object | Approximate size |
| --- | ---: |
| table heap | 31 GB |
| indexes | 54 GB |
| `uk_historical_order_book_fingerprint` | 14 GB |
| `ix_historical_order_book_trusted_snapshot` | 13 GB |
| `ix_historical_order_book_replay` | 13 GB |
| `ix_historical_order_book_sequence_verification` | 8.7 GB |
| primary key | 4.8 GB |

The dominant storage issue is therefore canonical Order Book evidence plus its
query/replay indexes, not the Market Intelligence database or export bundle.

### Market Data Configuration And Logs

- `HISTORICAL_OHLC_CAPTURE_ENABLED=true`.
- Five markets are configured.
- Intervals are `FIFTEEN_MINUTES`, `ONE_HOUR`, and `FOUR_HOURS`.
- Capture runs with a 15-minute delay and logs repeated acceptance of 199-200
  records per market and interval.
- `ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false`.
- `ORDER_BOOK_ACQUISITION_STARTUP_ENABLED=true`.
- No destructive retention gate is enabled.

The current state can therefore continue to grow through OHLC capture even
though new canonical Order Book evidence is disabled. The acquisition/subscription
gate being enabled is operationally separate from evidence persistence and must
not be interpreted as proof that new Order Book evidence is being durably
captured.

## Evidence And Research Eligibility

### Market Data Evidence

The current OHLC evidence population contains approximately 13,218 rows with
non-null `fetched_at`, `captured_at`, and `known_at`. This demonstrates that the
Market Data capture boundary can produce the provenance required by strict
temporal evaluation.

The current Order Book lifecycle contains:

```text
Sequences:          1,869
SEALED:             1,759
INVALIDATED:          110
Materialized segments: 0
Replay artifacts:      51
Dependencies:          51
Retention holds:        0
```

The initial bounded Order Book scope remains Kraken `BTC/USD`, linear
perpetual, depth 25. ADR-063 requires trusted, sequence-local replay with
`knownAt <= cutoff`; ADR-067 requires explicit dependency release before raw
evidence can become retirement-eligible. Neither condition is established for
the entire current population by this audit.

### Research Export

The recent bundle contains:

```text
Feature snapshots:             471
Snapshots created after cutoff: 470
Snapshots created by cutoff:     0
Export exclusions:              96
Eligible samples:                0
```

The export service fails closed when context sections lack `fetchedAt`. The
historical `analysis_executions.result.contextSections` payloads commonly have
`sourceOccurredAt` but no `fetchedAt`, and the runtime reports that the market
data capture time is missing. This is a provenance contract failure between the
Market Data evidence boundary and the Market Intelligence snapshot/export
boundary.

The current conclusion is:

- Historical OHLC rows are potentially usable evidence.
- Existing feature snapshots are not automatically repairable by rerunning the
  exporter because their persisted context lacks the required temporal fact.
- Creating new snapshots without fixing the capture-to-context propagation will
  continue producing exclusions or post-cutoff snapshots.
- The 85 GB Order Book population is not proven to improve dataset eligibility;
  storage size alone is not evidence of recoverability.

## Root Cause Classification

### Confirmed

1. The host is critically storage-constrained.
2. Order Book evidence and indexes dominate the Trading OS storage footprint.
3. Automatic destructive retention is not active.
4. OHLC capture remains active.
5. Research export eligibility is zero for the inspected bundle.
6. Context provenance required by the exporter is missing from historical
   context sections.
7. Snapshot creation is generally later than its evaluation cutoff.

### Supported Interpretation

The empty research dataset is primarily caused by a provenance/timing pipeline
gap, not by a simple lack of Market Data rows. The current Order Book storage
problem is a separate operational risk that must not be solved by deleting data
before replay and dependency semantics are validated.

### Unknown

- Exact live row count and byte growth rate for the Order Book table.
- Exact per-sequence `knownAt` distribution for the full table.
- Whether the 51 replay artifacts are sufficient to reproduce every dependent
  research or execution population.
- Whether any historical Order Book sequence can produce a valid strict context
  at the required cutoffs without a bounded replay test.
- Whether existing persisted context sections can be deterministically rebuilt
  from the OHLC evidence without introducing look-ahead or changing frozen
  identities.

## Decision Reconciliation

The measurements are consistent with the accepted architecture:

- ADR-063 defines immutable normalized evidence and strict known-at replay.
- ADR-067 rejects indefinite raw retention, age-only deletion, and cleanup under
  storage pressure without dependency release.
- Story 0142 is still Draft and explicitly forbids deleting or reclassifying the
  current population. It also requires bounded replay before operational use of
  the large table.

No architectural contradiction was found. The repository already contains the
correct direction; the missing work is controlled implementation and validation,
not a new retention policy.

## Classification Of Existing Data

| Category | Current classification | Basis |
| --- | --- | --- |
| A: strict research-eligible | Not demonstrated | No complete eligible export; context provenance/cutoff failures |
| B: potentially recoverable | OHLC rows; selected Order Book sequences | Provenance/replay and dependency validation still required |
| C: operational or diagnostic value | Invalidated sequences, acquisition diagnostics, live state | Useful operationally; not automatically strict research evidence |
| D: redundant/unjustified | Not proven | No deletion or compaction target can be safely named from current evidence |

Unknown dependency or provenance status remains protected, per ADR-067.

## Strategies

### Strategy 1: Repair The Existing Evidence Pipeline

Repair the Market Data to Market Intelligence contract so every context section
contains an explicit `fetchedAt`, `sourceOccurredAt` where available, and
stable evidence identity. Fix snapshot creation to respect the evaluation
cutoff, then validate against synthetic and bounded historical fixtures.

This is the smallest path to explainable research eligibility. It does not
recover historical snapshots whose required facts were never persisted.

### Strategy 2: Rationalize Storage First

Implement the already accepted lifecycle-aware retention path: bounded replay,
promotion, dependency release, dry-run preview, audit records, and only then
controlled retirement. Do not use age-only deletion or generic disk-pressure
cleanup.

This reduces operational risk but does not by itself make the research export
eligible.

### Strategy 3: Hybrid, Recommended

1. Apply an immediate human-approved storage-pressure response outside this
   audit, starting with independently verified Docker build-cache reclamation,
   not PostgreSQL data deletion.
2. Keep Order Book evidence capture disabled until filesystem headroom and
   persistence health are safe.
3. Repair provenance propagation and cutoff timing for new research snapshots.
4. Use bounded replay on a small selected population to classify recoverability.
5. Implement Story 0142 under its existing safeguards.
6. Promote only validated replay boundaries and retire raw evidence only after
   dependency release.

The hybrid strategy addresses the immediate availability risk and the actual
research blocker without weakening deterministic evidence rules.

## Required Gates Before More Collection

More collection should not resume as an unbounded default until all of the
following are true:

- at least 10% filesystem headroom is available and sustained;
- capture health reports the last successful durable persistence time;
- OHLC context sections preserve `fetchedAt` and cutoff semantics;
- a representative new snapshot passes export eligibility;
- Order Book replay has a bounded memory/query path;
- retention scheduler and destructive retirement remain independently gated;
- a documented storage budget and growth alert exist for each capture profile.

## Recommended Next Actions

1. Human decision: authorize a narrowly scoped Docker build-cache cleanup if
   those artifacts are not needed for active work. This audit did not execute it.
2. Implement and test provenance propagation and cutoff-safe snapshot creation;
   do not backfill or mutate frozen snapshots without an explicit decision.
3. Run a bounded, read-only replay classification against selected sealed
   sequences only after Story 0142 or an equivalent approved validation path is
   available.
4. Define whether promoted replay artifacts are sufficient for frozen research
   reproducibility. This is the remaining policy decision before raw evidence
   retirement.
5. Keep the current Order Book population untouched until dependency backfill,
   replay validation, and retention dry-run evidence are complete.

## Validation Record

Completed:

- repository and architecture history inspection;
- read-only Docker, filesystem, runtime, and PostgreSQL measurements;
- configuration and log inspection;
- research export and provenance inspection;
- reconciliation with ADR-063, ADR-067, and Draft Story 0142.

Not completed because of safety constraints:

- exact full-table row count;
- full-table temporal distribution scan;
- bounded replay execution against the live large population;
- retention preview or cleanup;
- production/module test execution, since this audit made no production code
  change.

## Relevant Artifacts

- `docs/architecture/adr/ADR-063.md`
- `docs/architecture/adr/ADR-067.md`
- `docs/architecture/reports/order-book-evidence-retention-replay-storage-investigation.md`
- `docs/architecture/stories/0142-make-order-book-replay-operational-and-retention-executable/story.md`
- `docs/architecture/stories/0142-make-order-book-replay-operational-and-retention-executable/implementation-plan.md`
- `market-data/src/main/resources/application.properties`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchDatasetExportService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/ContextSectionSummary.java`
