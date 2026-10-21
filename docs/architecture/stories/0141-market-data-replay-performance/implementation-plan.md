# Implementation Plan - Story 0141

## Overview

Reduce the measured Market Data strict Order Book replay latency without changing
canonical evidence, replay truth, sequence semantics, or the Market Intelligence
ownership boundary.

The plan is measurement-led. It does not preselect a query, index, checkpoint,
cache, or promoted artifact. The smallest candidate that demonstrates a material
benefit and semantic equivalence will be selected. If no candidate meets those
conditions, the Story will close with an evidence-based rejection and no source
change.

No implementation may weaken `knownAt <= cutoff`, trusted-sequence selection,
contiguity checks, provenance, fail-closed behavior, or ADR-067 dependency and
retention rules.

---

## Step 0 - Reconcile the Runtime Baseline

**Components:** `market-data`, PostgreSQL, the strict replay caller, and the
controlled local runtime used by Story 0140.

1. Reproduce representative cold-cache and warm-cache replay requests for the
   bounded market/depth scope.
2. Capture request identity, cutoff, selected sequence, evidence row count,
   payload size, query count, query-plan timing, JDBC transfer time,
   deserialization time, assembly time, and total latency.
3. Measure repeated semantic identities separately from distinct cutoffs so that
   any reuse candidate can be evaluated honestly.
4. Record the database, data volume, indexes, JVM, and cache state.

Baseline collection is observational and must not alter replay semantics or
provider-load controls.

## Step 1 - Isolate the Dominant Cost

Inspect `OrderBookEvidenceRepository.findTrusted()` and
`OrderBookReplayService.replay()` as separate phases:

1. Measure sequence candidate selection and anchor lookup.
2. Measure evidence-after-anchor query execution and returned payload volume.
3. Measure payload decoding and in-memory reconstruction independently.
4. Identify repeated replay requests from the Research critical path.
5. Confirm whether the dominant cost is query shape, row/payload volume, repeated
   calls, checkpoint distance, or another repository boundary.

**Decision gate:** do not implement an optimization until the dominant cost is
identified with reproducible evidence.

## Step 2 - Compare Candidate Accelerations

Evaluate only candidates supported by the baseline:

### Candidate A - Query or Index Improvement

- Test a narrower or better-ordered query and any narrowly scoped index.
- Compare plans, write cost, storage cost, and returned payload volume.
- Do not add an index unless the plan proves a durable benefit.

### Candidate B - Bounded Batch Replay

- Evaluate a request/read path that avoids repeated repository work for equivalent
  replay inputs.
- Preserve per-request cutoff, sequence identity, and failure isolation.
- Do not introduce unbounded batching or cross-request stale reuse.

### Candidate C - Checkpoint-Based Replay

- Evaluate existing derived checkpoints as trusted reconstruction anchors.
- Require source sequence, ordinal, cutoff, normalization version, and source
  fingerprints to remain verifiable.
- Prove that checkpoint selection cannot cross invalidated or ambiguous sequences.

### Candidate D - Promoted Replay Artifact

- Evaluate reuse of the existing ADR-067 promotion model for repeated semantic
  replay identities.
- Require atomic, idempotent promotion and explicit dependency protection.
- Treat missing, stale, invalidated, or unresolved dependency metadata as a miss
  or unavailable result, never as permission to serve the artifact.

Reject candidates that do not materially improve the measured critical path or
cannot preserve canonical replay semantics.

## Step 3 - Implement the Selected Candidate

1. Select the smallest candidate with a material measured improvement and a clear
   semantic-equivalence proof.
2. Keep canonical evidence append-only and authoritative.
3. Keep provider-specific transport behavior inside the Kraken adapter.
4. Add only the necessary repository/service methods, schema changes, or indexes.
5. Preserve the existing `OrderBookReplayResult` contract unless a separately
   approved architecture decision is required.
6. If the selected design changes ownership, lifecycle, retention, or the meaning
   of a promoted artifact beyond ADR-067, stop and prepare an ADR before source
   implementation continues.

If no candidate qualifies, make no source change and document the rejection.

## Step 4 - Prove Semantic Equivalence and Failure Safety

Compare canonical and optimized replay for fixtures covering:

- empty evidence;
- one trusted snapshot;
- long delta sequences;
- checkpoint boundaries;
- multiple sequences at one cutoff;
- invalidated and sealed sequences;
- sequence gaps and non-contiguous ordinals;
- future-known evidence;
- malformed or incomplete payloads;
- repeated identical semantic requests;
- promotion, dependency protection, release, and unresolved-hold cases where
  applicable.

The optimized path must return identical status, sequence identity, cutoff-bounded
provenance, book levels, source fingerprints, and trust outcome.

## Step 5 - Runtime and Operational Validation

1. Compare cold-cache and warm-cache latency before and after the selected change.
2. Compare query count, row count, payload volume, deserialization time, assembly
   time, database load, and total latency.
3. Validate repeated Research replay requests and distinct cutoff behavior.
4. Validate sequence invalidation and recovery boundaries using the existing Market
   Data lifecycle behavior.
5. Validate degraded, unavailable, and fail-closed outcomes when acceleration
   state is missing, stale, or invalid.
6. Record residual storage, retention, rebuild, and operational risks.

## Step 6 - Documentation Reconciliation

Update the Story implementation report with:

- baseline and post-change measurements;
- selected and rejected candidates;
- semantic-equivalence evidence;
- ownership, freshness, invalidation, rebuild, and provenance behavior;
- validation results and residual risks;
- whether an ADR was required or existing ADRs were sufficient;
- explicit follow-up work, if any.

No unrelated architecture, API, frontend, or operational documentation is
required.

---

## Conditional Files to Modify

- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookRetentionService.java`
  only if the selected candidate uses existing promotion/dependency semantics.
- Relevant Market Data migrations only if an index or derived persistence structure
  is proven necessary.
- Focused Market Data tests and the Story 0141 implementation report.

Expected to remain unchanged:

- Market Intelligence readiness and integrity contracts.
- Trading Core, Broker Service, Risk Domain, Gateway, and frontend behavior.
- Kraken checksum behavior and unrelated stream lifecycle behavior.

## Files to Create

- Story 0141 implementation report during implementation and documentation
  reconciliation.
- A new ADR only if the selected implementation materially extends ADR-067 or
  changes an ownership, retention, or provenance decision.

## Dependencies and Ordering

```text
approved Repository Analysis
    -> approved Implementation Plan
    -> runtime baseline
    -> cost isolation
    -> candidate comparison
    -> conditional implementation
    -> semantic equivalence
    -> runtime validation
    -> documentation reconciliation
    -> code review
```

Required dependencies:

- representative PostgreSQL Order Book evidence;
- controlled Market Data runtime;
- query-plan and database inspection access;
- existing replay, retention, and sequence test infrastructure;
- explicit human approval of this plan before source changes.

## Test Plan

### Market Data

- Run existing replay, persistence, retention, sequence, and lifecycle tests.
- Add focused tests for the selected acceleration and canonical equivalence.
- Cover invalidation, ambiguity, gaps, cutoff boundaries, and missing acceleration
  state.

### Database and Runtime

- Capture `EXPLAIN (ANALYZE, BUFFERS)` for the relevant queries.
- Compare cold and warm replay timing and payload/deserialization costs.
- Validate bounded market/depth runtime behavior.
- Verify no replay request can use future-known, stale, or invalidated evidence.

### Repository Validation

- Run affected Market Data Maven tests and relevant integration tests.
- Run `git diff --check`.
- Review the complete diff for unrelated changes.

No validation result is claimed before execution.

## Risks and Mitigations

- **Stale derived state:** require explicit invalidation and fail closed on unknown
  state.
- **Semantic drift:** compare canonical and optimized results on adversarial
  sequence and cutoff fixtures.
- **Storage growth:** measure checkpoint/artifact volume and apply ADR-067
  dependency protection before any retirement behavior.
- **False speedup:** separate query, transfer, deserialization, assembly, and
  total timing on repeated cold and warm runs.
- **Scope leakage:** stop for a new ADR when ownership or retention semantics
  change materially.

## Recommendation

Ready for implementation after explicit human approval of this plan.
