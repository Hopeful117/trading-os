# Implementation Plan - Story 0140

## Overview

Complete the deferred backend analysis from Story 0139 without introducing a new
product capability or moving the Market Data replay bottleneck into the wrong
service boundary.

The plan uses the approved Interpretation A:

- measure and optimize targeted Market Intelligence persistence reads only when
  evidence justifies the change;
- make and document the Step 4 projection decision;
- reject a shared Market Intelligence projection when it cannot materially improve
  the measured critical path;
- leave Market Data replay optimization to a separate future Story and ADR.

No implementation may change S1 semantics, readiness blockers, evidence policy,
remote budgets, timeout behavior, authentication, or service ownership.

---

## Step 0 - Reconcile the Baseline

**Components:** Story 0139 baseline, Market Intelligence runtime, PostgreSQL,
controlled authenticated Gateway environment.

1. Reproduce the existing cold-cache and warm-cache Research measurements using
   the scenario documented by Story 0139.
2. Capture readiness and integrity phase timings, query count, returned row count,
   payload volume, deserialization timing, cache hit/miss, and remote replay/fee
   timing where available.
3. Preserve the existing conclusion that replay is the dominant bottleneck unless
   new measurements disprove it.
4. Record the baseline and environment in the Story 0140 implementation report or
   a Story-local measurement artifact.

**Constraint:** baseline collection is observational and must not alter runtime
semantics or provider-load controls.

## Step 1 - Profile Targeted Persistence Candidates

**Components:**
`FeatureSnapshotRepository`, `ObservedMarketOutcomeRepository`, Spring Data
interfaces, JPA adapters, Research services, PostgreSQL migrations and tests.

1. Capture `EXPLAIN`/`EXPLAIN ANALYZE` evidence for the current feature snapshot
   population, latest-per-market, outcome-by-market, and outcome-range queries.
2. Measure row counts, selected columns, JSON payload sizes, domain deserialization
   time, and application-side filtering cost.
3. Map every readiness and integrity field to the data and semantic rule that
   produces it.
4. Identify reads that can be replaced by count, grouped, latest, or narrow
   projections without losing feature payload or artifact-dependent S1 semantics.
5. Compare candidate projections against the current implementation on fixtures
   covering empty, partial, stale, future-dated, duplicate-outcome, pending,
   unavailable, and strict-evidence cases.

**Decision gate:** do not change repository query shapes if the measured cost is
small relative to replay or if equivalence cannot be proven.

## Step 2 - Implement Only Justified Step 3 Changes

If Step 1 identifies a material and semantically safe improvement:

1. Add the smallest application port method needed by the Research contract.
2. Implement it in the JPA adapter and Spring Data interface with the existing
   market, cutoff, projection-version, and evidence-policy boundaries.
3. Keep non-trivial signal, regime, maturation, and fail-closed composition in
   Market Intelligence.
4. Avoid migrations and new indexes unless the query plan proves an existing index
   is insufficient and the operational trade-off is explicitly documented.
5. Add equivalence and failure-isolation tests before replacing the broad read.
6. Re-run the baseline and retain the change only if it improves the agreed metric
   without semantic, freshness, or database-load regression.

If no material improvement is demonstrated, make no source change and document
Step 3 as measured but rejected for this Story.

## Step 3 - Decide the Shared Projection / Precomputation Path

1. Measure the post-Step-3 critical path independently from the Market Data replay
   dependency.
2. Determine whether duplicated Market Intelligence reads remain material after
   excluding the known remote replay bottleneck.
3. If not material, reject Step 4 for this Story with evidence and preserve the
   existing independent readiness and integrity caches.
4. If material, stop before implementation and verify that the proposed projection
   can preserve independent availability, explicit freshness, invalidation,
   rebuild, stale-state, and fail-closed semantics under ADR-068.
5. Implement a shared projection only if the accepted ADR-068 and this approved
   plan both authorize the exact design. Otherwise prepare a revised plan.

Under the approved Interpretation A, a projection that requires Market Data-owned
replay precomputation is out of scope. That work must be proposed as a separate
Story/ADR.

## Step 4 - Documentation Reconciliation

Update the Story-local implementation report with:

- baseline and post-change measurements;
- query-plan and semantic-equivalence evidence;
- accepted or rejected Step 3 decision;
- accepted or rejected Step 4 decision;
- residual replay bottleneck and explicit follow-up recommendation;
- documentation outcome and no-change rationale for public contracts, Gateway,
  and service boundaries.

No new generic performance report or unrelated documentation is required.

---

## Files to Modify

Conditional on measurement:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/FeatureSnapshotRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ObservedMarketOutcomeRepository.java`
- corresponding Spring Data repository interfaces;
- corresponding JPA repository adapters;
- `ResearchReadinessService.java` and/or `ResearchIntegrityService.java` only if
  the selected read path changes;
- focused Market Intelligence tests;
- `docs/architecture/stories/0140-research-backend-projection-performance/implementation-report.md`.

Expected to remain unchanged unless evidence demonstrates otherwise:

- `trading-os-web` frontend contracts and components;
- Gateway routes and authentication;
- Market Data replay implementation;
- database schema and migrations.

## Files to Create

- `docs/architecture/stories/0140-research-backend-projection-performance/implementation-report.md`
  during implementation/documentation reconciliation.
- No new ADR is required for the approved Interpretation A; ADR-068 already
  governs the conditional Step 4 decision.

## Dependencies and Ordering

```text
approved Repository Analysis
    -> baseline reconciliation
    -> persistence profiling
    -> conditional Step 3 implementation
    -> post-Step-3 measurement
    -> Step 4 decision
    -> documentation reconciliation
    -> code review
```

Required dependencies:

- representative Research data;
- PostgreSQL query-plan visibility;
- controlled authenticated Gateway runtime;
- existing Market Intelligence and frontend test infrastructure;
- human approval of this Implementation Plan before source changes.

## Test Plan

### Market Intelligence

- Run existing readiness and integrity service/controller tests before and after
  any query change.
- Add repository/application equivalence tests for selected projections.
- Cover empty, partial, stale, future-dated, duplicate, pending, unavailable, and
  strict-evidence cases.
- Verify readiness and integrity failure isolation remains unchanged.

### Runtime and Database

- Compare cold-cache and warm-cache latency.
- Compare query count, row count, selected payload volume, deserialization cost,
  CPU, and database timing.
- Reconfirm remote replay/fee call count, duration, budget, and failure behavior.
- Confirm no Market Data replay implementation changes are included.

### Repository Validation

- Run affected Maven tests and applicable integration tests.
- Run frontend Research tests as regression validation.
- Run the Angular production build when frontend contracts are touched.
- Run formatting checks and `git diff --check`.
- Review the complete diff for unrelated changes.

No validation result is claimed before execution.

## Risks and Mitigations

- **Low-value SQL optimization:** reject changes whose gain is immaterial beside
  replay latency.
- **Semantic drift:** require fixture equivalence before replacing broad reads.
- **S1 regression:** preserve feature payload and artifact-dependent regime logic.
- **Stale projection:** do not add shared state without explicit freshness and
  invalidation semantics.
- **Scope leakage into Market Data:** create a separate Story/ADR rather than
  modifying replay ownership here.
- **Unrepresentative runtime:** record environment and use repeated cold/warm
  measurements rather than a single timing.

## Validation Checklist

- [ ] Baseline reproduced and documented.
- [ ] Query plans, row counts, payload sizes, and deserialization costs captured.
- [ ] Step 3 implemented only if measurable and semantically equivalent.
- [ ] Step 3 result compared against the replay-dominated critical path.
- [ ] Step 4 explicitly accepted or rejected with evidence.
- [ ] No Market Data replay architecture change included.
- [ ] Readiness/integrity semantics and failure behavior unchanged.
- [ ] Relevant tests, runtime measurements, formatting, and `git diff --check`
      pass.
- [ ] Implementation report completed.

## Recommendation

**Ready for implementation after human approval of this plan.**

This recommendation is technical only and does not authorize source changes.
