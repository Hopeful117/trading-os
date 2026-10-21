# Implementation Plan - Story 0142

## Overview

Design and validate a reproducible acceleration for strict Market Data Order Book
replay. The plan compares the existing checkpoint path with promoted replay
artifacts, selects only a materially beneficial and semantically equivalent
candidate, and rejects both candidates if their lifecycle cost is not justified.

Canonical normalized evidence remains authoritative throughout. No implementation
may proceed past the architecture decision point if the selected design changes
ADR-067 ownership, dependency, retention, or provenance contracts without an
accepted ADR update.

---

## Step 0 - Reconcile the Baseline

1. Reproduce the Story 0141 canonical replay baseline on representative sequence
   ages and cutoff positions.
2. Capture sequence selection, anchor selection, evidence row count, checkpoint
   distance, payload volume, deserialization, assembly, and total latency.
3. Measure cold-cache, warm-cache, repeated semantic identity, and distinct cutoff
   behavior.
4. Record sequence status, invalidation state, checkpoint coverage, artifact count,
   and database/index state.

Baseline collection is observational and must not alter canonical evidence or
retention state.

## Step 1 - Evaluate Checkpoint-Aware Replay

1. Inventory checkpoint frequency, source ordinals, known-at boundaries, source
   fingerprints, and sequence coverage.
2. Measure canonical replay from the latest eligible checkpoint against replay from
   the provider snapshot for the same cutoff.
3. Verify checkpoint selection never crosses sequence, cutoff, invalidation, or
   trust boundaries.
4. Measure checkpoint creation, persistence, storage, and rebuild cost.
5. Test missing, stale, malformed, partial, and invalidated checkpoint behavior.

**Candidate gate:** retain checkpoint acceleration only if it materially reduces
the critical path and its provenance can be verified without recreating the full
canonical scan.

## Step 2 - Evaluate Promoted Replay Artifacts

1. Identify repeated semantic replay identities in representative Research calls.
2. Measure lookup latency for existing promoted artifact structures and compare it
   with canonical replay.
3. Verify artifact identity includes market, depth, sequence, cutoff, source range,
   normalization, provider provenance, trust status, and source fingerprints.
4. Validate `matchesPersistedEvidence` behavior without allowing invalidated,
   future-known, incomplete, or unresolved dependency state to be served.
5. Measure promotion, idempotent reuse, dependency protection, release, and
   rebuild cost.
6. Test artifact miss, stale artifact, invalidated sequence, conflicting identity,
   and partial promotion outcomes.

**Candidate gate:** retain promoted artifacts only if repeated semantic requests are
common enough to justify lifecycle/storage complexity and the lookup is materially
faster than canonical replay.

## Step 3 - Select the Candidate or Reject Both

Compare candidates using:

- p50/p95 cold and warm latency;
- query count and returned evidence rows;
- payload/deserialization cost;
- storage and write-maintenance cost;
- promotion/checkpoint rebuild cost;
- invalidation and recovery behavior;
- semantic equivalence and provenance verification cost.

Select the smallest candidate that satisfies all ADR-069 invariants. If neither
candidate provides a material benefit with acceptable lifecycle cost, make no
source or schema change and document the rejection.

If the selected candidate changes ADR-067 contracts, ownership, retention, or
dependency semantics, stop and update ADR-069 before implementation.

## Step 4 - Implement the Selected Candidate

Conditional on Step 3 and required ADR acceptance:

1. Add the smallest Market Data port/service/repository change needed for the
   selected acceleration.
2. Keep canonical evidence append-only and preserve existing replay result
   semantics.
3. Make identity, provenance, sequence, cutoff, and normalization metadata
   explicit in the derived state.
4. Make creation/promotion atomic and idempotent.
5. Make invalidation, rebuild, degraded state, and failure behavior fail closed.
6. Avoid destructive retention actions unless separately authorized by ADR-067.

## Step 5 - Prove Semantic Equivalence

Compare canonical and optimized replay for:

- provider snapshot-only sequences;
- long delta sequences with checkpoints;
- multiple checkpoints;
- sealed and active sequences;
- sequence supersession and invalidation;
- sequence gaps and malformed evidence;
- cutoff immediately before and after a checkpoint;
- future-known evidence;
- missing, stale, conflicting, and partially rebuilt derived state;
- repeated identical semantic requests;
- dependency protection, release, and retention hold cases.

The optimized path must return identical status, sequence identity, cutoff-bounded
book levels, provenance, source fingerprints, and trust outcome.

## Step 6 - Runtime and Operational Validation

1. Compare canonical and optimized p50/p95 cold and warm latency.
2. Compare database reads, rows, payload volume, CPU, memory, and storage growth.
3. Validate rebuild and invalidation under sequence recovery.
4. Validate explicit unavailable/invalid outcomes when acceleration state is not
   trustworthy.
5. Record residual operational risks and the retention/dependency consequences.

## Step 7 - Documentation Reconciliation

Update the implementation report with:

- baseline and candidate measurements;
- selected or rejected candidate decision;
- semantic-equivalence evidence;
- lifecycle and ADR reconciliation;
- validation results and quality limitations;
- residual gap and follow-up work.

Update ADR-069 only if implementation changes the proposed boundary. No unrelated
documentation is required.

---

## Conditional Files to Modify

- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookReplayService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookRetentionService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookRetentionRepository.java`
- Existing checkpoint/persistence code only where required by the selected
  candidate.
- Focused Market Data tests and migrations only when justified by measurements.
- `docs/architecture/adr/ADR-069.md` only when the decision boundary changes.
- Story 0142 implementation report.

Expected to remain unchanged:

- Market Intelligence readiness/integrity behavior.
- Trading Core, Broker Service, Risk Domain, Gateway, and frontend behavior.
- Canonical evidence retention/deletion behavior unless separately approved.

## Dependencies and Ordering

```text
approved Repository Analysis
    -> approved Implementation Plan
    -> accepted ADR-069 when required
    -> baseline
    -> checkpoint evaluation
    -> promoted-artifact evaluation
    -> candidate decision
    -> conditional implementation
    -> semantic equivalence
    -> runtime validation
    -> documentation reconciliation
    -> code review
```

Required dependencies:

- representative PostgreSQL Order Book data;
- existing checkpoint and promotion foundations;
- controlled Market Data runtime;
- retention/dependency lifecycle test infrastructure;
- explicit human approval of this Implementation Plan before source changes.

## Test Plan

### Market Data

- Run replay, persistence, retention, capture, sequence, and lifecycle tests.
- Add canonical-versus-optimized equivalence tests for the selected candidate.
- Cover invalidation, cutoff, trust, missing-state, rebuild, and dependency cases.

### Database and Runtime

- Capture `EXPLAIN (ANALYZE, BUFFERS)` and application phase timings.
- Compare cold, warm, repeated, invalidated, and rebuilt paths.
- Measure storage and write-maintenance impact for durable derived state.

### Repository Validation

- Run affected Market Data Maven tests and relevant integration tests.
- Run applicable quality validation and record unavailable or pre-existing gates.
- Run `git diff --check`.
- Review the complete diff for unrelated changes.

No validation result is claimed before execution.

## Risks and Mitigations

- **Second source of truth:** keep canonical evidence authoritative and verify
  source fingerprints.
- **Stale derived state:** invalidate on sequence supersession and fail closed on
  unknown state.
- **Unbounded artifact growth:** require bounded semantic identities and ADR-067
  dependency release before retirement.
- **False performance gain:** compare complete cold/warm and repeated paths,
  including lifecycle overhead.
- **Architecture drift:** stop for ADR-069 acceptance when ownership or retention
  semantics materially change.

## Recommendation

Ready for implementation after explicit human approval of this plan and acceptance
of ADR-069 if the selected design requires the proposed boundary.
