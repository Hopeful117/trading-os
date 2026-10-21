# Repository Analysis - Story 0140

## Repository Baseline

- Branch: `main`
- HEAD: `b0d7a8c98062485caff3a01e93bc635ba17600bd`
- ADR-068 is accepted by explicit human approval and is present as an uncommitted
  repository artifact.
- Story 0140 is approved by explicit human approval and is present as an
  uncommitted repository artifact.
- No implementation source change was made for Story 0140.
- DevLog applicability was checked but the provider returned HTTP 429 because the
  configured quota was exhausted. Repository evidence is authoritative.

## Story and Architectural Context

Story 0139 implemented and validated:

- concurrent frontend readiness and integrity requests;
- bounded and concurrent historical OHLC lookup execution;
- readiness cache expiry correction;
- readiness phase and S1 remote-check instrumentation;
- Market Data replay profiling and temporary index experiments.

Story 0140 is intended to close the deferred backend performance work from 0139,
not to introduce a new product capability.

Relevant accepted constraints are:

- ADR-022: cache ownership, freshness, dependency validity, and explicit
  invalidation; TTL alone is not a business-validity rule.
- ADR-020/021/025/031/048/066: deterministic Market Intelligence projections,
  immutable evidence, explicit temporal scope, and fail-closed behavior.
- ADR-068: targeted persistence projections precede any shared/precomputed Research
  projection; the canonical evidence remains authoritative.

## Current Research Request Boundary

The frontend consumes two independent authenticated Market Intelligence endpoints:

```text
GET /api/v1/intelligence/research/readiness
GET /api/v1/intelligence/research/integrity
```

`ResearchService` exposes one typed Observable per endpoint. Story 0139 changed the
component orchestration so both requests start from the same refresh cycle and
preserved independent rendering and failure behavior.

No frontend aggregation or API contract change is required for Step 3. A combined
endpoint or shared response would be an architectural change and must preserve the
independent availability semantics of readiness and integrity.

## Current Readiness Implementation

`ResearchReadinessService.current()` validates configuration, uses a process-local
cache, and on a cache miss performs:

1. `FeatureSnapshotRepository.findByPopulation(...)` over the complete available
   snapshot history for configured markets;
2. `ObservedMarketOutcomeRepository.findByMarkets(...)` over all outcomes for the
   configured markets, followed by application-side horizon filtering;
3. bounded per-market historical OHLC evidence lookups;
4. signal and regime derivation from complete `FeatureSnapshot` domain payloads;
5. bounded, deduplicated replay and historical fee checks with explicit remote
   budgets and fail-closed handling;
6. readiness aggregation and process-local cache publication.

The current repository ports return complete domain objects. They do not expose
count-only, grouped, latest-per-market, status-aggregation, or narrow payload
projections.

The main Step 3 candidates are therefore:

- a targeted snapshot population projection;
- a targeted outcome aggregation/range projection;
- a combined read query only if it preserves the existing domain semantics;
- avoiding complete JSON payload deserialization when the Research contract does
  not require it.

The signal and regime computation is not a simple count. Any projection must
preserve the feature payload and artifact-dependent regime gate required by S1, or
demonstrate that the relevant derived values can be computed authoritatively
without duplicating or weakening those rules.

## Current Integrity Implementation

`ResearchIntegrityService.current()` has an independent process-local cache. On a
cache miss it performs:

1. one latest-snapshot query per configured market;
2. one bounded feature-snapshot population query over the maximum outcome horizon
   plus observation cadence;
3. one outcome range query over the same temporal window;
4. in-memory grouping by market, cutoff, and horizon;
5. acquisition lag, maturation, overdue, timestamp-anomaly, warning, and aggregate
   status calculation.

The integrity query window is already bounded. Its latest-per-market calls and its
separate snapshot/outcome loads are measurable candidates, but are not assumed to
be defects without query-plan and row-volume evidence.

## Persistence Structure

Current entities store large JSON payloads:

- `feature_snapshots.feature_payload`;
- `feature_snapshots.group_payload`;
- `feature_snapshots.provenance_payload`;
- outcome scalar facts and evidence/version fields in
  `observed_market_outcomes`.

Existing indexes include:

- `feature_snapshots (market_id, evaluation_cutoff)`;
- `feature_snapshots (projection_version)`;
- `observed_market_outcomes (market_id, evaluation_cutoff, horizon_seconds)`;
- `observed_market_outcomes (analysis_execution_id)`.

No migration should be proposed before measuring actual plans, row counts,
deserialization cost, and semantic equivalence. An index that improves one query
but materially increases storage or write cost must be evaluated as an operational
trade-off, not treated as a free optimization.

## Measured Baseline and Critical Finding

The Story 0139 implementation report and performance baseline provide the current
runtime evidence:

- snapshot load: approximately `24-76 ms` on the observed cold computations;
- outcome load: approximately `25-95 ms`;
- latest OHLC evidence: approximately `11-53 ms` after instrumentation;
- S1 replay/fee phase: approximately `32-36 s` wall-clock on the cold path;
- replay: `54` calls consumed approximately `172.486 s` cumulative and `27.010 s`
  wall-clock;
- fees: `68` calls consumed approximately `0.915 s` cumulative;
- SQL, snapshots, outcomes, and OHLC remained below approximately `60 ms`
  combined;
- Market Data replay profiling identified repository lookup as the dominant replay
  cost, with approximately `6-813 ms` per replay call and negligible replay
  assembly time;
- temporary replay indexes were removed because their storage/query trade-offs did
  not establish a safe durable improvement.

This materially changes the implementation priority originally suggested by the
0139 repository analysis: targeted snapshot/outcome projections remain a valid
measurement activity, but they are unlikely to materially reduce the current
Research critical path. The dominant bottleneck is a remote Market Data replay
dependency.

## Scope Contradiction Requiring Resolution

There is a material scope decision before an Implementation Plan can be safely
approved:

- Story 0140 and ADR-068 place Step 3/4 optimization primarily in Market
  Intelligence and prohibit changing service boundaries without a new decision.
- Current runtime evidence says the largest remaining latency is Market Data
  replay repository lookup, not Market Intelligence snapshot/outcome projection.
- A shared or precomputed Research projection owned only by Market Intelligence
  would not necessarily remove the remote replay cost unless it also owns or
  consumes a precomputed replay result.
- Moving replay precomputation or replay state into Market Data would affect the
  Market Data architecture, invalidation rules, retention/replay contracts, and
  likely ADR-063/067 boundaries.

The repository therefore does not support silently choosing between these two
interpretations:

### Interpretation A - close 0139 within current boundaries

Measure and optimize the snapshot/outcome projections, document that their impact
is negligible relative to replay, and close Step 4 with an evidence-based rejection
of a Market Intelligence shared projection. The replay bottleneck becomes a
separate follow-up Story with Market Data ownership and its own ADR analysis.

### Interpretation B - close the actual remaining bottleneck in 0140

Expand Story 0140 and ADR-068 to include a Market Data-owned precomputed replay
state or equivalent replay optimization, with explicit invalidation, sequence
integrity, retention, and provenance semantics. This is a material scope and
architecture expansion and cannot be inferred from the current approved artifacts.

## Recommended Repository Decision

The safest repository-compatible recommendation is **Interpretation A**:

1. complete the required Step 3 query-plan and semantic-equivalence investigation;
2. implement only a targeted projection if it has measurable benefit;
3. evaluate Step 4 after Step 3 and reject it if the dominant cost remains remote
   replay;
4. create a separate Market Data replay-performance Story/ADR if the product goal
   is to remove the remaining 23-41 second Research readiness bottleneck.

This recommendation keeps Story 0140 within its approved scope and avoids
introducing a Market Data architectural decision under a Market Intelligence
performance Story.

## Affected Files and Modules

### Market Intelligence

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchIntegrityService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/FeatureSnapshotRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ObservedMarketOutcomeRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataFeatureSnapshotRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataObservedMarketOutcomeRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaFeatureSnapshotRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaObservedMarketOutcomeRepository.java`
- `market-intelligence/src/main/resources/db/migration/`
- existing Research readiness/integrity and persistence tests

### Market Data

- replay client contract and adapter consumers are relevant for profiling only
  under Interpretation A;
- implementation changes are not authorized by the current Story/ADR.

### Frontend

- `trading-os-web/src/app/core/services/research.service.ts`
- `trading-os-web/src/app/core/models/research-readiness.model.ts`
- Research overview tests only for regression validation; no new frontend feature
  is required.

## Validation Status

- Repository inspection completed.
- Current Research services, ports, JPA repositories, entities, migrations, tests,
  and frontend contracts inspected.
- Story 0139 runtime evidence reconciled with current source.
- No implementation tests were run; this is Repository Analysis only.
- No source implementation change was made.
- `git diff --check` passed for the current artifacts.

## Decision Recorded

The human approved **Interpretation A**. Story 0140 remains within Market
Intelligence. Market Data replay optimization is explicitly deferred to a
separate future Story and ADR analysis because it changes a different ownership
and invalidation boundary.

## Approval Gate

Repository Analysis is approved for Story 0140. The next permitted artifact is the
Implementation Plan. No source implementation is authorized until that plan has
also received explicit human approval.
