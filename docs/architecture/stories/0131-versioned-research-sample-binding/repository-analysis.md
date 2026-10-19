# Repository Analysis - Story 0131

## Repository Baseline

```text
HEAD = 2aa4af74551a4076e21166fcbd995dcf3386813b
branch = main
origin/main = not revalidated in this analysis
worktree = untracked ADR-066, pre-existing v1 functional audit, and Story 0131 artifacts
```

The current repository state is authoritative for implementation. No source
code, historical research data, database migration, or runtime data was changed
for this analysis.

DevLog context was requested for Story 0131 but the provider returned an
insufficient-quota error. The repository, accepted ADRs, and existing
investigation artifacts are therefore the available sources of truth.

## Architectural Authority

The relevant architectural decisions are:

- ADR-060: `FeatureSnapshot` is immutable, versioned, and bounded by the
  evaluation cutoff.
- ADR-061: objective outcome selection uses complete immutable identity and
  fingerprint; latest-row and database-order selection are forbidden.
- ADR-062: `research-dataset-v1` is an objective FeatureSnapshot plus explicitly
  selected objective outcome contract.
- ADR-064: objective research remains separate from economic research and
  strategy-specific evaluation.
- ADR-066: exact FeatureSnapshot and ObservedMarketOutcome version binding must
  be explicit, fail closed, and reproducible; feature eligibility is declared
  per experiment.

ADR-066 is accepted. This Story implements that decision and must not redefine
the architecture.

## Current Objective Research Boundary

The current objective contract remains:

```text
research-dataset-v1
  = FeatureSnapshot
  + explicitly selected ObservedMarketOutcome
  + objective population and exclusion accounting
```

The following boundaries remain outside this Story:

- FeatureProjectionV1 remains the existing 43-feature schema.
- FeatureSnapshot truth remains distinct from objective future outcome truth.
- Order Book replay and liquidity evidence remain outside FeatureProjectionV1.
- Fee, funding, execution, fill, spread, slippage, and P&L semantics remain
  outside the objective dataset contract.
- Java remains authoritative for factual dataset construction and export.

## Existing FeatureSnapshot Identity

`FeatureSnapshot` currently contains:

```text
snapshotId
analysisExecutionId
marketId
evaluationCutoff
evidencePolicy
featureProjectionVersion
contextFingerprint
values
groups
provenance
fingerprint
createdAt
```

It exposes:

- `fingerprint()` for snapshot content identity;
- `identityFingerprint()` derived from market, cutoff, evidence policy,
  projection version, context fingerprint, and sorted provenance;
- `hasSameSemanticContent(...)` for semantic-content comparison.

`FeatureSnapshotRepository` already supports exact lookup through
`findByFingerprint(String)`. `ExactFeatureSnapshotResolver` validates the
resolved market, cutoff, projection, context, content fingerprint, and identity
fingerprint and never falls back to population lookup.

This existing exact resolver is compatible with ADR-066 and should be reused or
aligned rather than replaced with a new latest-row abstraction.

## Current Population Contract

`ResearchPopulationSpec` currently declares:

- population policy version;
- market and cutoff bounds;
- feature projection and evidence policy;
- outcome definition and horizons;
- sampling cadence;
- one global `acceptedFeatureMissingness` set;
- explicit outcome fingerprints keyed by market, cutoff, and horizon.

It does not declare selected FeatureSnapshot identities. Therefore, the builder
cannot distinguish an intended snapshot version from another immutable snapshot
at the same market and cutoff.

The current global missingness set also cannot express per-experiment required
feature names. It treats every feature status outside the accepted set as a
dataset exclusion, regardless of whether that feature is used by the experiment.

## Current Dataset Builder Behavior

`ResearchDatasetBuilder` currently:

1. Validates the requested FeatureProjectionV1 version.
2. Sorts all supplied snapshots by market, cutoff, and snapshot fingerprint.
3. Includes every snapshot inside the population bounds.
4. Detects repeated semantic identities in the supplied list and excludes a
   duplicate or rejects conflicting content.
5. Builds one sample per included snapshot and requested horizon.
6. Selects outcomes by explicit outcome fingerprint and outcome definition.
7. Applies global accepted missingness before outcome selection.
8. Computes dataset fingerprint material from the population, selected outcomes,
   sample fingerprints, and exclusion counts.

The critical reproducibility gap is step 3: adding another valid immutable
snapshot version at an existing market and cutoff changes the input population
without changing `ResearchPopulationSpec`. Sorting does not make that selection
explicit or stable.

The builder currently handles missing selected outcomes as an exclusion reason,
but Story 0131 requires missing explicitly selected artifact identities to fail
closed rather than silently selecting another version.

## Current Manifest and Export

`DatasetManifest` stores the complete `ResearchPopulationSpec`, schema,
outcome schema, counts, exclusions, creation time, dataset fingerprint, and
fingerprint material.

`ResearchDatasetBundleExporter` serializes population fields and selected outcome
fingerprints into `dataset-manifest.json`. It does not currently serialize
selected FeatureSnapshot identities or experiment-level required feature names.

The export is already deterministic for ordered collections and includes a
sample checksum. The implementation should extend this existing manifest rather
than introduce a second objective dataset manifest format.

## Existing Tests and Coverage

`ResearchDatasetBuilderTest` already covers:

- one sample per snapshot and horizon;
- missingness and typed JSONL export;
- unselected, incomplete, and unavailable outcomes;
- deterministic ordering and dataset fingerprints;
- bundle export and repeated byte-identical exports;
- corrected outcome identity changing the sample and dataset fingerprint;
- duplicate snapshots and conflicting duplicate outcomes;
- invalid schema and projection-version behavior;
- global feature missingness exclusion.

`ExactFeatureSnapshotResolverTest` verifies exact lookup and explicitly verifies
that population lookup is never used as a fallback.

The tests do not yet cover:

- explicit selected FeatureSnapshot identity in `ResearchPopulationSpec`;
- later snapshot versions being ignored by a frozen population;
- missing selected snapshot identity;
- duplicate selected snapshot identity;
- snapshot identity material in the dataset manifest;
- per-experiment required feature names;
- ignoring non-required missing features;
- deterministic exclusion of a missing required feature;
- Q1 with zero required domain features.

## Scope and Module Assessment

Only `market-intelligence` is directly involved. The expected affected areas are:

- `domain.research` population, requirement, manifest, and exclusion contracts;
- `application.research` dataset builder, exact resolution, and bundle exporter;
- focused unit tests and existing cross-language fixture assertions where the
  manifest contract changes.

No other service boundary is implicated. No production database schema change is
currently indicated because the required artifact identities already exist in
the domain and repository lookup contracts. If implementation reveals that
durable population specifications or historical manifest persistence require a
schema change, implementation must stop for architectural review.

## Risks and Decisions for the Implementation Plan

The Implementation Plan must resolve the following without changing ADR-066:

- whether the population stores one selected snapshot fingerprint per
  `ResearchOutcomeKey` or a horizon-independent snapshot selection key;
- whether missing selected identities are represented as a build failure or a
  deterministic fail-closed result with an explicit exclusion/error contract;
- how required feature names are canonicalized and validated against
  FeatureProjectionV1;
- how the existing global missingness policy is retained or replaced without
  weakening schema validity;
- how manifest serialization remains deterministic and compatible with the
  committed cross-language fixture expectations.

These are implementation-contract details, not permission to select a latest
row, infer a snapshot from cutoff alone, or change FeatureProjectionV1.

## Validation Assessment

The affected Maven module is `market-intelligence`. Validation should include:

- focused population-contract tests;
- exact snapshot-resolution tests;
- builder reproducibility and fail-closed tests;
- feature-requirement eligibility tests;
- manifest/export determinism tests;
- existing `ResearchDatasetBuilderTest` and
  `ExactFeatureSnapshotResolverTest` suites;
- the repository-standard `market-intelligence` Maven verification;
- `git diff --check`.

No Q1 economic evaluation or historical data mutation is part of validation.

## Analysis Outcome

The Story is implementable within the accepted ADR-066 boundary. The current
repository contains the required immutable identity primitives and exact
snapshot repository lookup, but the objective population contract and builder
do not yet use them for explicit snapshot selection.

An Implementation Plan is required before source changes because the Story
changes several connected immutable contracts and the exported manifest format.
The next permitted workflow stage is human approval of this Repository Analysis.
