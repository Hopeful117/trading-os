# Story 0109A - Python Research Dataset Consumer and Validation

## Status

Proposed - pending repository analysis and implementation-plan approval.

## Goal

Make the deterministic Market Intelligence research dataset safely materializable,
portable, validated, and inspectable from Python.

The boundary is:

```text
STRICT_OBSERVED historical evidence
    -> IntelligenceContext @ T0
    -> deterministic analyses
    -> FeatureSnapshot
    -> ResearchDatasetBuilder
    -> DatasetManifest + ResearchSample
    -> dataset-manifest.json + samples.jsonl
    -> Python contract validation
    -> population and missingness diagnostics
```

This Story ends after factual dataset validation and diagnostics. It does not
train or evaluate a machine-learning model.

## Context

Stories 0107A through 0107C and 0108A/0108B establish strict historical
evidence, deterministic analysis, immutable feature snapshots, immutable
objective outcomes, explicit outcome-version selection, and a deterministic
research dataset boundary.

The current implementation has the Java domain boundary but requires an
operational export bundle and a fail-closed Python consumer. Current runtime
data may legitimately produce zero eligible samples; the implementation must
report that state rather than weaken temporal semantics or fabricate history.

## Scope

### Java export boundary

- Add the smallest bounded, read-only population queries for FeatureSnapshots
  and ObservedMarketOutcomes when missing.
- Preserve explicit outcome fingerprint selection from ADR-061.
- Keep population and eligibility logic in `ResearchDatasetBuilder`.
- Add a bounded research dataset export application service.
- Serialize a deterministic `dataset-manifest.json`.
- Serialize deterministic `samples.jsonl` using the existing sample exporter.
- Materialize the two files as one logically consistent dataset bundle.
- Provide a reproducible explicit local/developer invocation.
- Preserve `STRICT_OBSERVED`, `knownAt`, cutoff, lineage, and no-lookahead
  semantics.
- Do not introduce an unbounded scheduler or automatic historical backfill.

### Python research consumer

- Create a repository-owned offline Python research package.
- Use one clean Python project configuration and minimal dependencies.
- Validate the actual Java manifest and JSONL contract with Pydantic or an
  equivalent strict contract layer.
- Reject unsupported dataset, feature-projection, and outcome-definition
  versions.
- Reject unknown features, missing declared features, wrong types, invalid
  statuses, invalid timestamps, invalid decimals, invalid lineage, and
  conflicting duplicate samples.
- Preserve explicit missingness and validate decimals before any future float
  conversion.
- Expose a validated dataset object separate from any future DataFrame view.
- Provide reproducible `validate` and `diagnose` CLI commands.
- Report factual population, coverage, horizon, exclusion, outcome, and
  missingness diagnostics.
- Treat an empty but valid dataset as valid with zero eligible samples.
- Add a Java-generated golden bundle consumed by Python tests.

## Dataset Contract

The canonical bundle is:

```text
dataset-manifest.json
samples.jsonl
```

The manifest must preserve the existing Java concepts:

- dataset definition version;
- population specification;
- feature schema;
- outcome schema;
- sample, eligible, and excluded counts;
- exclusion diagnostics;
- dataset semantic fingerprint.

Each JSONL row must preserve the existing sample semantics:

- sample identity;
- market and evaluation cutoff;
- horizon;
- sample status and exclusion reason;
- typed features and explicit missingness;
- objective outcome;
- feature, outcome, and sample lineage fingerprints.

The Java semantic dataset fingerprint must not be changed by operational export
metadata such as filesystem paths or export timestamps.

## Architecture Constraints

- Java remains authoritative for historical facts, deterministic features,
  outcomes, sample eligibility, and dataset identity.
- Python only validates, loads, and diagnoses exported facts.
- No future outcome evidence may enter IntelligenceContext, FeatureSnapshot,
  feature fingerprints, or the Python feature matrix.
- No implicit latest-outcome selection is allowed.
- No provider fallback is allowed for strict historical generation.
- No historical backfill evidence mode is introduced.
- No public REST API, Gateway route, frontend change, or deployed Python
  microservice is introduced.
- No Python process may call Broker, Execution, Risk mutation, TradePlan, or
  Challenge APIs.
- Export must fail safely rather than leave a misleading partial bundle.
- If cross-language fingerprint verification is underspecified, stop and report
  the blocker rather than inventing an approximate implementation.

## Out Of Scope

- ML training or model evaluation;
- Ridge, LinearRegression, scikit-learn, prediction, or metrics;
- train/test, walk-forward, purge, or embargo logic;
- statistical preprocessing such as scaling, encoding, or imputation;
- feature selection, correlations, or target research;
- model persistence, registry, online inference, or shadow mode;
- historical research backfill;
- new indicators, News, LLM, RAG, agents, or sentiment;
- strategy, backtesting, P&L, fees, slippage, or sizing;
- Risk, Broker, Execution, Trading Core, Challenge, or Angular changes;
- FastAPI, Kafka, Spark, Airflow, MLflow, feature store, or data warehouse.

## Validation Requirements

### Java

- bounded deterministic population selection;
- explicit outcome fingerprint selection;
- manifest serialization;
- JSONL serialization;
- deterministic repeated export;
- zero-sample export;
- excluded sample preservation;
- bundle consistency and atomic/complete export behavior;
- idempotent FeatureSnapshot generation if an explicit invocation is added.

### Python

- valid Java-generated bundle;
- unsupported versions fail closed;
- unknown or missing features fail closed;
- wrong feature types fail closed;
- invalid statuses, timestamps, decimals, and lineage fail closed;
- duplicate and conflicting samples fail closed;
- empty valid dataset succeeds;
- diagnostics are deterministic.

### Integration

- `market-intelligence` tests and verification;
- Java golden bundle generation;
- Python tests against the real Java-generated bundle;
- Python validation and diagnostics commands;
- PostgreSQL query validation when local infrastructure is available;
- confirmation that no ML training occurs;
- `git diff --check`.

## Expected Current Data Result

The implementation must verify current runtime data rather than hardcode an
assumption. It is acceptable for the result to be:

```text
Dataset contract: VALID
Eligible samples: 0
ML readiness: NOT READY
Reason: insufficient strictly observed FeatureSnapshot population
```

Zero valid eligible samples are a truthful outcome of this Story, not a failure
that should be bypassed.

## Documentation

Document the actual commands for:

- generating the dataset bundle;
- validating the bundle from Python;
- running diagnostics;
- interpreting `STRICT_OBSERVED` and `knownAt`;
- distinguishing a valid empty dataset from an invalid dataset;
- understanding that ML training is intentionally absent.

Do not create a new ADR unless implementation reveals a new architectural
authority or evidence mode that cannot be governed by ADR-057 through ADR-062.

## Acceptance Criteria

1. Java can deterministically materialize a complete manifest-plus-JSONL
   research bundle from a bounded `ResearchPopulationSpec`.
2. The export preserves dataset identity, explicit outcome selection, feature
   types, missingness, exclusions, and lineage.
3. Python validates the actual Java-produced bundle before any DataFrame use.
4. Unsupported contracts and malformed or conflicting data fail closed.
5. Decimal values are validated before any future float conversion.
6. Diagnostics expose factual counts, coverage, horizons, exclusions, outcomes,
   and missingness.
7. A valid empty dataset is represented honestly and does not crash validation.
8. No historical knowledge is fabricated and no strict replay guarantee is
   weakened.
9. A Java-to-Python golden contract test passes.
10. No ML model is trained and no trading authority is exposed to Python.

## Follow-Up

Story 0109B may implement the first offline baseline experiment only after the
validated dataset has sufficient strictly observed data. It should define its
own target, horizon, evaluation, preprocessing, and reproducibility contract.
