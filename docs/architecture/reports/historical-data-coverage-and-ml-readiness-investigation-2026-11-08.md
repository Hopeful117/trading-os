# Historical Data Coverage And ML Readiness Investigation

**Date:** 2026-11-08

**Mode:** read-only runtime, database and source investigation

**Repository:** `/home/ludo/workspace/trading-os`

**DevLog:** unavailable in the current tool environment (`MCP error -32000:
Connection closed`). Current repository code, runtime database evidence and
existing reports were used instead.

## Executive Summary

The current platform has enough persisted OHLC evidence to support bounded
historical analysis, but it does not have a sufficiently populated,
multi-market feature/outcome population for unsupervised ML.

The important distinction is between **endpoint readiness** and **dataset
readiness**:

- The inspected GBP/USD readiness request can be reported as `READY` for the
  requested window because it has one strict feature snapshot and one
  available outcome.
- That result represents one sample, not a statistically useful historical
  dataset.
- The active research lifecycle is configured for one BTC market, so the
  target GBP/USD market is not being continuously populated by the current
  scheduler.
- The feature projection contract is deterministic and versioned, but it can
  emit explicit missingness. The one GBP/USD snapshot contains three
  unavailable pullback features; whether that is acceptable is a population
  specification decision, not an ingestion failure.
- The readiness service does not perform the complete dataset eligibility
  checks implemented by `ResearchDatasetBuilder`. In particular, it checks
  snapshot provenance through execution-result presence, but does not enforce
  the exporter's temporal evidence map or pair every returned snapshot with an
  exact selected outcome.

**Final bottleneck classification: `FEATURE_PROJECTION_POPULATION_GAP`**

The next milestone should be bounded, operator-approved population of the
selected market set through the existing lifecycle/reconstruction path,
followed by an actual dataset export and offline validation. A new ingestion
service, new public historical endpoint, or ML model should not be started
from this investigation.

## Safety Boundary

No runtime code, database row, schema, configuration, provider state,
credential, grant, service process or broker state was changed. No historical
backfill, export, reconstruction, training run or provider synchronization was
performed.

The running services and databases were inspected read-only. The worktree was
already dirty from earlier work; this investigation added only this report.

## Current Runtime Population

The following counts were measured from the running local PostgreSQL services.
They are database counts, not claims about external provider availability.

| Store | Count | Coverage observed |
|---|---:|---|
| `market-data.historical_ohlc_evidence` | 13,624 | Persisted OHLC evidence exists; target-market coverage is bounded and recent |
| `market-data.price_observations` | 482,543 | Large observation store; not itself a feature/outcome dataset |
| `market-data.historical_order_book_evidence` | 0 | No persisted order-book evidence |
| `market-data.historical_order_book_replay_artifact` | 0 | No replay artifact population |
| `market-data.historical_funding_evidence` | 0 | No persisted funding evidence |
| `market-intelligence.feature_snapshots` | 525 | Three markets represented; 523 belong to BTC |
| `market-intelligence.observed_market_outcomes` | 3,562 | Six markets represented |
| `market-intelligence.observation_evidence` | 1,364 | Evidence rows exist for observation records |
| `market-intelligence.analysis_executions` | 909 | Executions exist across 15 markets |
| `market-intelligence.research_executions` | 10 | Research execution records exist |
| `market-intelligence.historical_reconstruction_lifecycles` | 0 | No persisted reconstruction lifecycle |
| `market-intelligence.historical_dataset_export_job` | 0 | No persisted export job |

The count of OHLC evidence is materially larger than the count of feature
snapshots, but those stores have different semantics. OHLC rows are inputs;
they do not become ML samples until strict analysis execution, feature
projection, outcome evaluation and exact dataset selection have all occurred.

## Target Market Evidence

The inspected target market was GBP/USD:

```text
marketId: 8b336ec3-1557-4432-b052-ae569e041dac
provider: cTrader
product type: CFD
```

Target OHLC evidence was measured as:

| Interval | Rows | Earliest close | Latest close |
|---|---:|---|---|
| 15 minutes | 859 | 2026-10-25T21:00:00Z | 2026-11-06T21:45:00Z |
| 1 hour | 345 | 2026-10-19T12:00:00Z | 2026-11-06T21:00:00Z |
| 4 hours | 236 | 2026-09-14T13:00:00Z | 2026-11-06T18:00:00Z |

The measured target OHLC rows passed the inspected strict predicate:

```text
closed = true
close_time <= cutoff
known_at <= cutoff
synthetic = false
```

This establishes that the target market has a bounded strict OHLC input
population. It does not establish that every desired historical cutoff can be
reconstructed, because the evidence begins in late September/October and is
not a full-range backfill.

The target market has exactly one feature snapshot and one outcome:

| Entity | Value |
|---|---|
| Feature cutoff | `2026-11-06T16:45:00Z` |
| Feature snapshot | `9509f1a5-6633-4d0e-943e-460e0cbbf867` |
| Analysis execution | `1aef27cf-a423-474b-8a0d-3f0e64a9db3d` |
| Execution status/mode | `COMPLETED` / `PASSIVE` |
| Evidence policy | `STRICT_OBSERVED` |
| Projection | `feature-projection-v1` |
| Outcome cutoff | `2026-11-06T16:45:00Z` |
| Outcome horizon | 1 hour |
| Outcome status | `AVAILABLE` |

For the requested readiness window
`2026-11-01T00:00:00Z` to `2026-11-08T20:00:00Z`, the observed funnel is:

| Stage | Count |
|---|---:|
| Target feature snapshots in window | 1 |
| Target feature snapshots before 2026-11-01 | 0 |
| Target outcomes in window | 1 |
| Available target outcomes in window | 1 |
| Target order-book evidence | 0 |
| Target replay artifacts | 0 |

The one snapshot has a non-empty provenance payload and its execution has a
completed result, so the current readiness service classifies its provenance
as valid. This is internally consistent for the one observed sample.

## Lifecycle Coverage

The active lifecycle configuration was inspected in the running environment:

```text
RESEARCH_LIFECYCLE_ENABLED=true
RESEARCH_LIFECYCLE_MARKET_IDS=e23ba62a-995f-4755-8deb-11a71ab3788b
RESEARCH_LIFECYCLE_OBSERVATION_CADENCE=PT1H
RESEARCH_LIFECYCLE_OUTCOME_HORIZONS=PT1H,PT4H,PT24H
RESEARCH_ARTIFACT_POPULATION_ENABLED=true
RESEARCH_ARTIFACT_POPULATION_MAX_PER_RUN=1
RESEARCH_ARTIFACT_POPULATION_DEPTH=25
```

The configured market is BTC, not the inspected GBP/USD market. The lifecycle
submits observations only for configured market IDs and then projects the
completed executions for those markets. Therefore the current scheduler cannot
continuously populate GBP/USD. The single GBP/USD snapshot is historical
runtime state or a manually/previously generated record; it is not evidence
that the current lifecycle is covering GBP/USD.

The lifecycle also catches artifact-population failures and continues. Existing
runtime logs report `Forbidden` during replay-artifact population. This does
not explain the absence of GBP/USD feature snapshots because GBP/USD is not in
the active lifecycle market list, but it does prevent the configured BTC path
from being treated as fully healthy for replay-dependent research.

## Readiness Semantics

`HistoricalReadinessService` performs these main operations:

1. Selects feature snapshots by market, cutoff range, projection version and
   `STRICT_OBSERVED` policy.
2. Selects outcomes by market and cutoff range.
3. Counts all `AVAILABLE` outcomes independently of the selected snapshots.
4. Marks provenance valid when every returned snapshot has non-empty provenance
   and its analysis execution has a result.
5. Returns `READY` when snapshots exist, at least one available outcome exists,
   provenance is valid, and no simple blocker is present.

Consequences:

- `availableEvidenceCount` and `featureSnapshotCount` both return the selected
  snapshot count. They are not independent counts of raw historical evidence.
- `eligibleOutcomeCount` is a range/status count and is not joined to a
  feature snapshot by `(marketId, evaluationCutoff, horizon)`.
- Readiness does not apply `ResearchDatasetBuilder`'s exact outcome fingerprint
  selection, feature-schema validation, feature-missingness requirements or
  temporal-evidence map.
- Readiness checks execution-result presence, but that is weaker than proving
  every source evidence item was known at the feature cutoff.

For the target request this does not create a false positive about the single
sample's exact observed outcome: the database contains matching cutoff and
available outcome. It does mean that `READY` must not be interpreted as
"sufficient for unsupervised ML" or as proof that a larger requested
population is exportable.

## Feature Contract And Missingness

`feature-projection-v1` is deterministic and versioned. It contains OHLC,
market-structure and trend-context features across bias, setup and trigger
roles. The target snapshot contains 41 feature values. Most are `PRESENT`.
The following three values are explicitly `UNAVAILABLE`:

```text
trend.bias.pullback_qualified
trend.setup.pullback_qualified
trend.trigger.pullback_qualified
```

The projection contract preserves this missingness rather than fabricating a
value. `ResearchDatasetBuilder` excludes a sample only when a required feature
has disallowed missingness. The population specification must therefore
explicitly decide whether these features are required and whether
`UNAVAILABLE` is accepted. The runtime snapshot alone cannot answer that
dataset-policy question.

The projection is suitable as a deterministic supervised/unsupervised input
schema in principle, but one sample and a mostly categorical feature space are
not evidence of a useful unsupervised regime model. A real ML-readiness claim
requires a materially larger, time-distributed population, stable feature
availability, explicit missingness policy and leakage-safe temporal validation.

## Identity And Conflict Handling

Feature snapshot identity is based on market, evaluation cutoff, evidence
policy, projection version, context fingerprint and sorted analyzer
provenance. The persisted identity and snapshot fingerprints are both unique.
When an existing identity is found, the repository compares semantic content
and raises `Feature snapshot identity has conflicting content` if the content
differs.

This behavior is safe for immutable facts: it prevents silently overwriting a
snapshot. The observed runtime conflict therefore indicates a deterministic
identity/content inconsistency or concurrent/repeated projection problem; it is
not evidence that the system should overwrite the existing row. The conflict
needs a separate implementation Story if it persists after the population
configuration is corrected.

## ML Readiness Assessment

| Requirement | Finding | Status |
|---|---|---|
| Persisted strict OHLC inputs | 13,624 rows overall; bounded GBP/USD rows pass strict checks | Partial |
| Multi-market feature population | 525 snapshots, 523 concentrated in BTC; target GBP/USD has 1 | Not sufficient |
| Exact feature/outcome pairing | Present for the one target sample; not established for a broad population | Partial |
| Mature outcomes | One target `AVAILABLE` outcome; broader outcomes exist but are not joined here | Partial |
| Temporal feature evidence | Export builder supports the check, readiness endpoint does not enforce it | Not proven |
| Stable missingness policy | Contract preserves missingness; population policy not supplied | Not proven |
| Order-book/funding context | No persisted target order-book/replay/funding rows | Missing |
| Exported, validated dataset | No export job persisted and no new export performed | Not established |
| Unsupervised ML suitability | One target sample cannot support it | Not ready |

The evidence supports **data available for continued accumulation**, not
**data sufficient for unsupervised ML**.

## Recommended Next Milestone

Create one focused, human-approved milestone with this scope:

1. Select the intended market set and explicit cutoff range.
2. Configure the existing lifecycle for that market set, or use the existing
   controlled historical reconstruction path where passive accumulation cannot
   cover the required range.
3. Resolve the replay-artifact `Forbidden` failure for the selected research
   path without weakening authorization or bypassing Gateway/service
   boundaries.
4. Let feature snapshots and outcomes accumulate until the target cadence,
   horizons and minimum sample count are met.
5. Define the feature-required and accepted-missingness policy.
6. Run the existing dataset export and offline validation.
7. Only after the bundle passes temporal, provenance, schema and eligibility
   checks, assess unsupervised regime-learning experiments.

Out of scope for that milestone: a new provider ingestion service, a new raw
historical-data API, changes to the agent authorization model, automatic
backfill without an explicit provenance decision, model training, or broker
execution behavior.

## Validation Sources

- `market-intelligence/.../HistoricalReadinessService.java`: readiness counts
  and verdict semantics.
- `market-intelligence/.../FeatureSnapshotProjectionService.java`: strict
  projection and artifact validation.
- `market-intelligence/.../ResearchDatasetBuilder.java`: canonical sample
  eligibility and exclusion rules.
- `market-intelligence/.../JpaFeatureSnapshotRepository.java`: immutable
  identity/conflict behavior.
- `market-intelligence/.../FeatureSnapshot.java`: semantic identity material.
- `market-intelligence/.../FeatureProjectionV1.java`: feature schema and
  explicit missingness behavior.
- `market-intelligence/.../ResearchLifecycleService.java`: lifecycle market
  selection, projection and outcome evaluation.
- `market-intelligence/.../ResearchLifecycleScheduler.java`: scheduled
  execution and Market Data discovery guard.
- `docker-compose.yml`: active lifecycle environment.
- Running `market-data` and `market-intelligence` PostgreSQL databases:
  read-only count and target-market queries described above.
