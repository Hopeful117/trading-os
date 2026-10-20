# Repository Analysis - Story 0133

## Repository Baseline

- Branch: `main`
- HEAD: `0cd04438311b0faf9d05f211b541ba50203f8061`
- HEAD includes the merge of Story 0132 navigation refactoring.
- Working tree contains one pre-existing untracked protected file:
  `docs/architecture/reports/v1-functional-audit.md`.
- No source changes were present before this analysis.
- Story 0132 is present at
  `docs/architecture/stories/0132-refactor-trading-os-sidebar-navigation/story.md`.
- DevLog context was requested and unavailable because the provider returned an
  insufficient-quota error. Repository evidence remains authoritative.

## Current Research Ownership

Market Intelligence owns the current research lifecycle, immutable feature
snapshot projection, objective market-outcome maturation, and Research
Readiness. Trading Core, Broker Service, Gateway, and Angular are not owners of
Research truth.

The lifecycle is disabled by default and, when enabled, uses an explicitly
configured market set, an hourly default observation cadence, configured outcome
horizons, and a one-minute default scheduler delay. It aligns observations to
cadence boundaries, uses strict observed evidence, projects completed
executions, and evaluates matured horizons.

## Current Readiness Architecture

`ResearchReadinessService` currently computes a cached readiness projection. It
reads configured feature snapshots and configured-horizon outcomes, reports
latest Market Data evidence timestamps, and computes the existing S1 readiness
gate data. The authenticated endpoint is:

`GET /api/v1/intelligence/research/readiness`

The internal service-facing endpoint remains:

`GET /internal/v1/research/readiness`

The Gateway already routes the public intelligence path and enforces the
authenticated boundary. The readiness cache defaults to 30 seconds.

## Evidence Sources Relevant to Integrity

### FeatureSnapshot

`FeatureSnapshotRepository` supports population lookup by market, cutoff range,
projection version, and evidence policy. The persistence table has a market and
cutoff index. The current readiness and lifecycle population reads are broad
over the entire configured history, so a new integrity projection must not reuse
those unbounded reads for repeated health checks.

`FeatureSnapshot` provides deterministic market identity, evaluation cutoff,
projection/evidence identity, and creation chronology. It is the appropriate
source for acquisition freshness, but it does not provide a separate durable
acquisition-success ledger or a `knownAt` value for the lifecycle itself.

### Research lifecycle configuration

The configured market universe and observation cadence are available through
`ResearchLifecycleProperties`. The lifecycle's aligned cutoff and configured
fixed delay provide existing timing semantics. No independent research start
date, market-session calendar, or explicit continuity window is currently
defined.

### ObservedMarketOutcome

`ObservedMarketOutcomeRepository` currently exposes an unbounded market lookup
and exact market/cutoff/horizon lookup. The domain exposes maturity, status,
computed-at, evaluation cutoff, and observation-coverage facts. The status
values distinguish pending, available, incomplete, and unavailable evidence.

The outcome table has a market/cutoff/horizon index, so a bounded range query is
feasible without exposing outcome values.

### Replay and fee evidence

Replay and historical-fee checks are already part of S1 readiness and are
bounded by an existing remote-check budget. The repository does not currently
provide a separate recent operational-availability or freshness ledger for
either source. This Story should reference the existing readiness gate data
rather than invent duplicate operational semantics.

## Integrity Semantics Assessment

### Acquisition freshness - implementable

The deterministic source is the latest persisted FeatureSnapshot evaluation
cutoff per configured market. The expected cutoff can be derived from the
existing aligned observation cadence and configured lifecycle delay, rather than
an arbitrary wall-clock threshold.

The read model should distinguish at least:

- `HEALTHY`: the latest snapshot meets the configured expected cutoff;
- `DEGRADED`: a snapshot exists but is behind by no more than one configured
  cadence;
- `STALE`: the snapshot is older than that bounded grace;
- `NO_DATA_YET`: the configured market has no snapshot in scope;
- `UNKNOWN`: configuration or repository/dependency failure prevents a safe
  determination.

The exact aggregate rule and response vocabulary belong in the Implementation
Plan. No frontend calculation should reproduce them.

### Observation continuity - defer

The repository has a configured cadence and market universe, but it has no
authoritative research start boundary, market availability/session calendar, or
bounded continuity window. Inferring 24/7 expected observations or introducing
an arbitrary lookback would create unsupported scientific semantics. Do not
implement missing-observation counts in this Story unless a current accepted
contract supplies the missing boundary.

### Outcome maturation - implementable in a bounded window

For each configured horizon, snapshots whose maturity is due can be compared
with outcome existence/status using a time window bounded by the maximum
configured horizon plus one cadence. The projection may expose eligible,
available, pending, incomplete/unavailable, overdue, and latest-computed-at
metadata, but must never expose outcome values or derive result quality.

The repository should gain a bounded query shape rather than using
`findByMarkets` for repeated integrity reads.

### Timestamp coherence - partially implementable

Existing semantics support cheap checks such as outcome maturity matching its
evaluation cutoff plus horizon, computed-at not preceding the evaluation cutoff,
and future-dated evaluation cutoffs being absent from the observed window.
Only checks with explicit lifecycle/domain support should be added. A generic
data-quality framework is not justified.

### Replay and fee operational health - reference only

Readiness already owns replay and fee coverage semantics. The current code does
not expose a distinct operational freshness signal for these providers. This
Story should not duplicate or reinterpret their readiness gates.

### Signal distribution - preserve existing metadata only

Existing signal and aggregate-regime counts may remain readiness/accumulation
metadata. They must not be transformed into performance interpretation.

### Overall integrity state - implement only if fail-closed rules stay explicit

An aggregate state is useful only if it is a deterministic composition of the
implemented dimensions. Missing configuration, repository failure, or an
unknown critical dimension must not become healthy. If the composition cannot
be documented without arbitrary weighting, expose individual dimensions and an
explicit unknown state instead.

## API Recommendation

Use a sibling authenticated endpoint conceptually named:

`GET /api/v1/intelligence/research/integrity`

Keep `/readiness` semantically stable. Add the matching internal endpoint only
if an existing service consumer requires it; the current frontend needs the
authenticated public projection. Reuse the existing Market Intelligence
controller and Gateway route conventions.

## Persistence and Query Assessment

Do not add a table. Compute the read model from immutable snapshots and outcome
rows. Add bounded repository methods and use existing market/cutoff indexes.
Scope queries to configured markets and the current bounded maturation/freshness
window. Avoid the current unbounded readiness population reads for integrity
polling.

## Cache and Refresh Recommendation

Reuse the existing readiness cache duration or a shared research projection
cache. Do not introduce frontend polling until backend cache semantics are
defined. The current Research Overview uses manual refresh only; a proportionate
refresh interval may be considered in the Implementation Plan, but hourly
research cadence does not justify frequent polling.

## Frontend Assessment

The existing Research Overview already separates the readiness gates from a
results-unavailable boundary and has explicit loading/error states. Add a
compact Acquisition Integrity section only after the backend contract is fixed.
The section should project status, latest evidence, maturation warnings, and
unknown/degraded states while keeping Readiness and Results visually separate.

## Decision

**B - PARTIAL_IMPLEMENTATION** is recommended.

Implementable now:

- bounded acquisition freshness;
- bounded outcome maturation existence/status/timing;
- narrowly defined timestamp coherence;
- fail-closed authenticated read model and frontend projection.

Deferred because current semantics are insufficient:

- observation continuity and missing-period counts;
- separate replay operational health;
- separate fee operational health;
- any weighted integrity score or unsupported aggregate heuristic.

## Risks and Protection

- The current readiness service performs broad historical reads; copying that
  shape into integrity polling could become expensive as S1 accumulates.
- No durable acquisition-success ledger exists, so freshness reflects persisted
  FeatureSnapshot evidence, not scheduler execution success directly.
- Runtime validation may be limited by disabled lifecycle configuration and
  unavailable authenticated dependencies.
- No historical research evidence may be deleted, rewritten, or repaired.
- Performance outcomes are outside the inspection and implementation boundary.

## Implementation Gate

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan or source changes. The next permitted stage is:

`Approve and continue Story 0133`
