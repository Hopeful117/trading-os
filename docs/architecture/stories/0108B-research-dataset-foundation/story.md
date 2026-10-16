# Story 0108B - Research Dataset Foundation

## Status

Implemented locally - pending review.

## Goal

Create the deterministic Java boundary between strict historical feature
snapshots, explicitly selected objective market outcomes and future Python
research.

## V1 Scope

- Add a versioned `ResearchPopulationSpec`.
- Build one typed `ResearchSample` per feature snapshot and configured horizon.
- Preserve feature types and explicit missingness.
- Keep objective outcomes separate from features and labels.
- Emit immutable `DatasetManifest` counts, schema and fingerprint.
- Export deterministic JSON Lines without provider payloads.
- Record exclusion reasons instead of silently dropping rows.

## Population Semantics

V1 uses `ONE_PER_FEATURE_SNAPSHOT`, only snapshots in
`[startCutoffInclusive, endCutoffExclusive)`, and `STRICT_OBSERVED` feature
evidence. Each horizon requires an explicit selected outcome fingerprint. A
correction therefore creates a different sample and dataset fingerprint.

## Persistence Decision

No new Flyway migration or dataset table is introduced. The existing immutable
FeatureSnapshot and outcome repositories remain authoritative. V1 produces a
deterministic manifest and JSONL export from supplied immutable facts; durable
dataset materialization is deferred until a repository population/listing
contract is required.

## Out Of Scope

- Feature or outcome calculation.
- Latest/superseded outcome inference.
- Labels, strategy evaluation, backtesting or P&L.
- Python, pandas, scikit-learn or model training.
- Train/test splitting, imputation, scaling or categorical encoding.
- Public API, Gateway, frontend, Risk, Broker or Execution changes.

## Validation

- Focused deterministic research dataset tests.
- Full `market-intelligence` Maven tests and verification.
- PostgreSQL persistence validation where local infrastructure is available.
- `git diff --check`.
