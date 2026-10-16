# Repository Analysis - Story 0109A

Date: 2026-10-16 · Branch: `main` · HEAD: `416fa2cf2df1205d4004004066aae0940f4ea`

## Workflow and Authority

- The repository is clean except for the untracked Story 0109A artifact.
- Local `HEAD` and local `origin/main` both resolve to
  `416fa2cf2df1205d4004004066aae0940f4ea`.
- The remote fetch attempt previously failed because the configured SSH
  security-key operation was refused. Git authentication was not changed.
- Story 0108B is merged in the local main history through PR #118.
- DevLog context was requested but unavailable because the provider reported
  exhausted quota.
- Current repository code and runtime data are authoritative for physical state.

## Architectural Authority

ADR-057 through ADR-062 establish the relevant boundary:

- `knownAt`, capture origin, and `STRICT_OBSERVED` determine whether evidence
  was available at T0.
- Strict replay never falls back to provider history.
- Objective outcomes are immutable facts, not ML labels.
- FeatureSnapshot is derived only from deterministic T0 artifacts.
- Outcome selection is explicit by immutable fingerprint; no latest-row policy is
  allowed.
- Java owns factual dataset construction; Python may only validate and diagnose
  exported facts.
- No new ADR is required if this Story remains offline, read-only, captured-only,
  and does not introduce a new evidence mode or runtime service.

## Current Java Boundary

The current implementation contains:

- `FeatureSnapshotProjectionService` for explicit projection from one analysis
  execution;
- `FeatureSnapshotRepository` with only fingerprint lookup and save-if-absent;
- `ObservedMarketOutcomeRepository` with exact lookup and one-key candidate
  lookup;
- `ResearchPopulationSpec` with market/cutoff/version/horizon/missingness and
  explicit outcome fingerprints;
- `ResearchDatasetBuilder` with eligibility, exclusion, deterministic ordering,
  schema validation, and dataset fingerprint construction;
- `DatasetManifest` as an in-memory record;
- `ResearchDatasetJsonlExporter` for sample rows only.

No production caller for `FeatureSnapshotProjectionService` was found. No
population listing query, dataset export application service, manifest serializer,
bundle writer, or reproducible research invocation exists.

## Runtime Data Verification

The local PostgreSQL runtime currently reports:

| Fact | Value |
|---|---:|
| Persisted FeatureSnapshots | 0 |
| Persisted outcomes | 7 |
| Available outcomes | 6 |
| Distinct persisted outcome horizons | 1 |
| Historical OHLC rows | 598 |
| Markets with historical OHLC | 1 |
| Historical OHLC known before close | 0 |

The historical OHLC rows cover one BTC/USD perpetual market and an apparent
calendar range from `2026-09-12T16:00Z` to `2026-10-15T21:30Z`, but their `knownAt`
semantics do not establish equivalent strict observed knowledge at those older
cutoffs. This Story must not convert those rows into strict FeatureSnapshots by
backdating knowledge.

The honest current result remains a valid empty research population: zero
FeatureSnapshots, zero candidate ResearchSamples, and zero eligible samples.

## Python Repository State

No `pyproject.toml`, `requirements.txt`, Python package, Python service, or
Python research convention exists in the repository. The only Python file found
is an unrelated coverage utility script.

The smallest coherent direction is a repository-owned offline package under a
new `research/ml`-style directory, with one `pyproject.toml`. It must not be a
deployed `ai-analytics` service and must not include FastAPI or ML dependencies
in this Story.

## Required Implementation Shape

### Java

1. Add bounded deterministic snapshot population retrieval by the population
   scope, cutoff range, projection version, and evidence policy.
2. Add bounded outcome retrieval sufficient to feed explicit fingerprint
   selection without choosing a latest correction.
3. Add a read-only research export application service that invokes the existing
   `ResearchDatasetBuilder`.
4. Add deterministic manifest serialization.
5. Reuse the existing JSONL sample exporter.
6. Write `dataset-manifest.json` and `samples.jsonl` as one complete local
   bundle, avoiding partial successful exports.
7. Add an explicit bounded developer/research invocation, not a scheduler.

### Python

1. Add strict Pydantic-style models for the actual Java bundle.
2. Validate versions, schema fields/types, statuses, timestamps, decimals,
   lineage, and duplicate identities before any DataFrame conversion.
3. Preserve an empty valid dataset as valid.
4. Add deterministic validation and diagnostic CLI commands.
5. Add a real Java-generated golden bundle consumed by Python tests.

## Material Design Risks

### Dataset fingerprint interoperability

`ResearchDatasetBuilder` computes the semantic fingerprint from private canonical
material. `DatasetManifest` exposes only the resulting fingerprint and does not
expose the canonical material or an export checksum. Python cannot safely
recompute the semantic fingerprint from the current manifest alone without
reimplementing underspecified Java internals.

The implementation plan must choose one explicit, architecture-preserving
approach:

- treat the Java semantic fingerprint as opaque and validate bundle counts,
  sample fingerprints, versions, and lineage; or
- expose a deterministic canonical fingerprint material/checksum contract
  separately from the semantic dataset fingerprint.

Python must not invent an approximate hash algorithm.

### Manifest serialization

The domain manifest contains sets, maps, and `createdAt`. Default Jackson
serialization must not become the semantic identity authority. Operational
timestamps and filesystem paths must remain outside semantic fingerprint
material.

### FeatureSnapshot generation

The existing projection service requires a strict completed analysis execution
and matching persisted artifacts. A bounded invocation may be added, but it
must not introduce provider fallback, broad automatic replay, or a high-frequency
research scheduler.

### Export source materialization

The current repository interfaces do not expose all snapshots for a bounded
population. The new query must return deterministic candidates, while the
builder remains responsible for explicit selection and exclusions.

## Validation Plan

- Focused Java tests for population queries, manifest serialization, deterministic
  repeated export, zero-sample bundles, excluded rows, and explicit outcome
  selection.
- Python fixture tests for valid, empty, unsupported, malformed, duplicate, and
  conflicting bundles.
- A Java-generated golden bundle consumed by Python.
- `mvn -q test` and `mvn -q verify` for `market-intelligence`.
- Python test and CLI commands using the selected project configuration.
- PostgreSQL query validation against the actual schema where practical.
- `git diff --check`.
- No ML training command and no Risk/Broker/Execution interaction.

## Scope Assessment

The Story can remain within existing architecture if it stays offline and
read-only. No new ADR is currently required. The implementation should stop and
request architectural guidance if it requires a new historical evidence mode, a
deployed Python service, provider fallback, or an invented cross-language
fingerprint contract.

## Analysis Outcome

The current architecture is sufficient for a bounded Java export plus offline
Python validation, but the operational path is absent. The current data is not
ML-ready and is expected to validate as an empty dataset. The next permitted
workflow stage is an Implementation Plan, after explicit human approval of this
Repository Analysis.
