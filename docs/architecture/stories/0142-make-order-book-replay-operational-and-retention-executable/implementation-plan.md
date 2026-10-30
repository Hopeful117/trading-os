# Implementation Plan - Story 0142

## Approval

This plan requires explicit human approval before production code changes begin.

## Overview

Make Order Book replay bounded without changing its evidence contract, then add
the smallest retention coordinator compatible with the existing Spring
`@Scheduled` conventions. The implementation will preserve canonical evidence,
sequence boundaries, provider-specific trust, replay provenance, retention
dependencies, holds, audit records, and fail-closed behavior.

No current runtime evidence will be deleted, compacted, reclassified, or
rewritten. No runtime restart, deployment, cTrader subscription, or large-table
migration will be performed during implementation or validation.

## Phase 1 - Replay Contract and Repository Query

1. Preserve the current replay entry point and artifact fast path.
2. Retain the existing unique eligible-sequence selection and latest trusted
   snapshot/checkpoint selection semantics.
3. Add a repository method that retrieves post-base evidence in deterministic
   keyset batches rather than returning the complete evidence list.
4. Filter in SQL by the actual replay predicates:
   - one sequence;
   - trusted evidence;
   - delta evidence after the selected base ordinal;
   - `known_at <= cutoff`;
   - ascending `sequence_ordinal`.
5. Use `sequence_ordinal` as the stable progression key. Do not use offset
   pagination or timestamp-only ordering.
6. Reuse the existing replay indexes initially. Use plain `EXPLAIN` only when
   safe; do not add or apply a speculative large-table index.
7. Add a configurable positive batch size and a configurable maximum replay
   evidence count. Exceeding the maximum returns an explicit unavailable or
   invalid result rather than allocating unbounded memory.

Likely files:

- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/config/*`
- `market-data/src/main/resources/application*.yml` only when an existing
  configuration convention requires a default to be documented

## Phase 2 - Bounded Reconstruction

1. Introduce a small replay accumulator in the Market Data service boundary.
2. Seed the accumulator from exactly one trusted snapshot/checkpoint.
3. Consume each batch in canonical ordinal order and apply deltas immediately.
4. Verify that every accepted delta is contiguous with the expected ordinal.
5. Reject unexpected non-delta evidence, trust failures, sequence changes,
   cutoff violations, and continuity gaps as the current replay does.
6. Preserve `occurredAt`, `knownAt`, provider/provider-symbol, origin,
   normalization version, trust, sequence range, and source fingerprint
   semantics in the returned result.
7. Keep the existing source-fingerprint result contract. Collect fingerprints
   only within the configured replay bound; do not allow the collector to grow
   without limit.
8. Preserve the existing level ordering, quantity application, result
   fingerprint, and explicit unavailable/invalid reasons.

Likely files:

- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- a focused package-local accumulator/model if reuse is justified

## Phase 3 - Replay Tests

Add or extend tests for:

- latest eligible snapshot/checkpoint selection;
- updates after the selected base;
- cutoff exclusion based on `knownAt`;
- one-sequence enforcement;
- invalid or untrusted base rejection;
- checkpoint and provider snapshot provenance;
- equal timestamp ordering by sequence ordinal;
- keyset progression across multiple batches;
- empty result and missing reconstruction base;
- maximum replay evidence bound;
- strict gap detection;
- representative Kraken provider chronology;
- representative cTrader local-observation semantics and generation isolation;
- old/new result equivalence for levels, ordering, trust, provenance, and
  fingerprints.

Use synthetic fixtures and mocks or isolated database fixtures. Do not populate
the runtime PostgreSQL database or create a gigabyte-scale test dataset.

## Phase 4 - Retention Coordinator

1. Add a Market Data scheduler component following the existing `@Scheduled`
   convention.
2. Add configuration with safe defaults:
   - scheduler disabled by default;
   - conservative configurable fixed delay and initial delay;
   - no new generic job framework.
3. Use a local non-blocking lock so a second execution skips while one run is
   active.
4. When enabled, invoke `previewRetirement()` exactly once per run. Preserve
   its existing dry-run operation-recording side effect.
5. Log a bounded summary containing mode, candidate count, retired count,
   duration, and failure status. Do not log evidence rows or payloads.
6. If the scheduler is enabled while destructive retirement is disabled, keep
   the run dry-run-only and do not call destructive retirement.
7. If both gates are enabled, invoke the existing `retire()` method per
   candidate and retain its dry-run fingerprint and batch safeguards.
8. Do not duplicate candidate selection, dependency checks, hold checks, trust
   checks, sequence checks, or audit recording in the scheduler.
9. Preserve incremental progress: one candidate retirement call remains bounded
   by `retirementBatchSize`; partial operations are handled by the existing
   service/repository.

Likely files:

- `market-data/src/main/java/com/hope/trading/market_data/scheduler/OrderBookRetentionScheduler.java`
- `market-data/src/main/java/com/hope/trading/market_data/config/OrderBookRetentionProperties.java`
- existing Market Data configuration files only where needed

## Phase 5 - Retention Tests

Add focused coordinator tests proving:

- scheduler disabled by default;
- enabled dry-run does not delete evidence;
- destructive retirement remains independently gated;
- scheduler overlap is skipped;
- preview is invoked once per run;
- candidate retirement uses the existing service rather than duplicate logic;
- exceptions produce bounded failure observability and release the lock;
- batch size remains controlled.

Retain and run existing retention service and persistence tests covering:

- active sequence protection;
- sealed or sufficiently old invalidated sequence requirement;
- unresolved dependency protection;
- unresolved hold protection;
- requirement for at least one released dependency;
- untrusted artifact protection;
- dry-run fingerprint requirement;
- destructive-retirement-disabled failure;
- resumable bounded retirement.

## Phase 6 - Schema and Runtime Safety

- No migration is planned initially.
- Existing indexes will be evaluated against the final bounded query using
  static inspection and safe plain `EXPLAIN` only.
- If a new index is proven necessary, add only the minimum additive migration
  and report that runtime application is deferred because the evidence table is
  approximately 87 GB and the filesystem is critically constrained.
- Do not run `EXPLAIN ANALYZE`, replay requests, index builds, deletes,
  `VACUUM FULL`, reindexing, Docker cleanup, or runtime restarts as part of this
  implementation.

## Phase 7 - Documentation Reconciliation

Update only the Story implementation report or canonical Market Data operational
documentation if the final behavior changes configuration or operational usage.
Document that:

- replay is bounded and fail-closed at its configured limit;
- scheduler enablement and destructive retirement are independent gates;
- preview records a dry-run operation;
- current runtime evidence remains untouched;
- capture semantic compression is not implemented.

Do not modify ADR-063, ADR-065, ADR-067, ADR-069, or ADR-079 unless the
implementation reveals a material architectural conflict. If such a conflict is
found, stop and request a separate ADR decision.

## Validation Commands

Run focused tests first, using the repository Maven wrapper and actual test
class names after implementation. Then run the relevant Market Data module
suite/build and:

```text
git diff --check
```

Quality validation must report applicable, executed, passed, failed, blocked,
and non-applicable checks. Runtime validation is explicitly deferred for disk
safety.

## Expected Files

### Production

- `OrderBookEvidenceRepository` and/or a focused replay repository helper.
- `OrderBookReplayService` and/or a focused bounded accumulator.
- `OrderBookRetentionProperties`.
- New retention scheduler/coordinator.
- Configuration only if required by repository conventions.

### Tests

- Existing replay service/repository tests.
- Existing retention service/persistence tests.
- New bounded replay and retention coordinator tests where existing coverage is
  insufficient.

### Migration

None expected. Any required migration must be justified by the final query and
must not be applied to the current constrained runtime database.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Replay still loads too much evidence | Keyset batches, checkpoint base, explicit maximum, fail-closed bound |
| Fingerprint provenance becomes incomplete | Preserve existing fingerprint contract and verify equivalence tests |
| Checkpoint is not independently valid | Reuse trust, sequence, cutoff, and provenance checks; reject unverifiable bases |
| Scheduler deletes unexpectedly | Disabled scheduler default plus independent destructive gate |
| Preview operation history grows | Conservative configurable frequency and one preview per run |
| Concurrent retirement | Local non-blocking overlap lock |
| Runtime disk exhaustion | No runtime deployment, migration, replay, or cleanup during validation |
| Provider semantics regress | Kraken and cTrader-specific replay regression fixtures |
| Retention safeguards are duplicated or weakened | Coordinator delegates all eligibility and mutation decisions to existing service |

## Deliverables

- Bounded replay implementation.
- Controlled retention coordinator disabled by default.
- Focused replay and retention tests.
- Configuration and operational documentation reconciliation where required.
- Implementation report with validation and runtime-safety results.

## Implementation Preconditions

Implementation is not authorized until this plan is explicitly approved.
