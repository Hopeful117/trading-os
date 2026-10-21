# Implementation Report - Story 0140

## Decision

**NO SOURCE PROJECTION CHANGE - STEPS 3 AND 4 REJECTED WITH EVIDENCE**

Story 0140 closes the deferred backend decision from Story 0139 without adding a
new product capability or introducing speculative persistence architecture.

The measured local Research persistence path is not the current critical-path
bottleneck. The dominant remaining cost is the remote Market Data replay
dependency identified by Story 0139. That optimization requires a separate
Market Data Story and ADR because it changes a different ownership and
invalidation boundary.

## Changes

- Added ADR-068 for Research projection ownership, freshness, invalidation, and
  measurement gates.
- Added Story 0140 as the continuation of Story 0139.
- Added the approved Repository Analysis and Implementation Plan.
- Profiled the active Market Intelligence PostgreSQL Research tables and query
  plans.
- Did not add targeted persistence projections, indexes, migrations, shared
  projections, or new cache authority because the measured benefit was not
  justified.
- Did not modify Market Data replay behavior.

## Measurement Evidence

The controlled local `market-intelligence` PostgreSQL database contained:

| Relation | Live rows | Total size |
|---|---:|---:|
| `feature_snapshots` | `22` | `568 kB` |
| `observed_market_outcomes` | `913` | `712 kB` |

Existing relevant indexes were present:

- `feature_snapshots (market_id, evaluation_cutoff)`;
- `feature_snapshots (projection_version)`;
- `observed_market_outcomes (market_id, evaluation_cutoff, horizon_seconds)`;
- `observed_market_outcomes (analysis_execution_id)`.

Observed query plans on the active local data:

| Query | Result rows | Execution time |
|---|---:|---:|
| Full feature snapshot population with cutoff/version/policy filters | `118` | `0.237 ms` |
| Outcomes for configured market population | `907` | `0.705 ms` |
| Bounded outcome range for configured market population | `907` | `2.789 ms` |

The Story 0139 runtime instrumentation remains the more representative critical
path evidence for the Research page:

- snapshots: approximately `24-76 ms`;
- outcomes: approximately `25-95 ms`;
- OHLC evidence: approximately `11-53 ms`;
- S1 replay/fee phase: approximately `32-36 s` cold-cache wall-clock;
- replay calls: approximately `27.010 s` wall-clock for the measured cold run;
- fees: approximately `0.915 s` cumulative remote time.

The persistence path is therefore not a credible explanation for the observed
`23-41 s` readiness latency in the measured environment.

## Step 3 Decision

Targeted persistence projections were evaluated and rejected for this Story.

Reasons:

- current query execution is sub-millisecond to low-single-digit milliseconds on
  the active data;
- the current domain projections need feature payloads and artifact-dependent
  signal/regime semantics, so a count-only query would not replace the complete
  readiness calculation without duplicating business rules;
- outcome filtering and maturation semantics require cutoff, horizon, status,
  timestamp, and evidence fields that are already covered by the existing bounded
  query/index;
- no measured query-plan or deserialization gain justified a new repository
  projection or schema change.

No source implementation change was made for Step 3.

## Step 4 Decision

The shared or precomputed Market Intelligence Research projection was rejected for
this Story.

The measured dominant cost is remote Market Data replay. A shared readiness
projection in Market Intelligence would not remove that cost unless it also
introduced Market Data-owned replay state or precomputation. That would change
Market Data replay ownership, invalidation, retention, sequence integrity, and
provenance semantics.

Such work is explicitly deferred to a separate Story and ADR. No shared cache,
materialized view, durable projection, or TTL-only cache was introduced.

## Semantic and Safety Guarantees

- S1 thresholds, blockers, signal semantics, and readiness values were unchanged.
- Integrity acquisition and maturation semantics were unchanged.
- Strict evidence policy and temporal cutoffs were unchanged.
- Replay, fee, OHLC, timeout, concurrency, and remote-call budgets were unchanged.
- No frontend, Gateway, authentication, or service-boundary behavior changed.
- No Market Data replay implementation or schema was changed.
- No stale or incomplete projection can be served because no new projection was
  introduced.

## Validation

- PostgreSQL table cardinality and relation-size inspection: passed.
- PostgreSQL index inventory: passed.
- `EXPLAIN (ANALYZE, BUFFERS)` for current snapshot/outcome reads: passed; timings
  recorded above.
- Targeted Research service/controller tests:
  `mvn -q -Dtest=ResearchReadinessServiceTest,ResearchIntegrityServiceTest,ResearchLifecyclePropertiesTest,InternalResearchReadinessControllerTest,ResearchReadinessControllerTest,ResearchIntegrityControllerTest test`:
  passed.
- Targeted persistence tests:
  `mvn -q -Dtest=FeatureSnapshotPersistenceTest,ObservedMarketOutcomePersistenceTest test`:
  passed.
- `git diff --check`: passed.
- Existing Story 0139 frontend suite/build and full Market Intelligence/Market Data
  suite evidence remain recorded in the Story 0139 implementation report; no
  application source changed in Story 0140.

Existing Mockito, Flyway, Spring Boot, and H2 warnings were emitted by the test
environment. No test failure was reported.

## Documentation Reconciliation

Documentation update: required and completed.

The Story-local Repository Analysis, Implementation Plan, and this Implementation
Report record the measured query plans, Step 3/4 decisions, residual bottleneck,
scope boundary, and separate follow-up requirement. No broader architecture or
runtime documentation was changed beyond ADR-068 because no new implementation
boundary was introduced.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: repository source, accepted ADRs, Story 0139 evidence, and runtime
  database measurements were sufficient. No curated note was required or
  modified.

## Residual Risk and Follow-up

Research readiness remains dominated by Market Data replay latency on cold cache.
The next optimization should be a separate Market Data-owned replay performance
Story with an ADR covering precomputed replay state, invalidation, sequence
integrity, retention, and provenance. That follow-up is not part of Story 0140.

Story 0141 performed that follow-up measurement. It rejected the tested index
alternatives and leaves the remaining gap as a future checkpoint-aware or
promoted-artifact replay design under ADR-063 and ADR-067.

## Review Status

Implementation, documentation reconciliation, Code Review, and human final
approval are complete. No commit, push, or merge was performed.
