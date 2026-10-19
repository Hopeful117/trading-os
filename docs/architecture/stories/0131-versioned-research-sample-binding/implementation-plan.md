# Implementation Plan - Story 0131

## Approval

This plan requires explicit human approval before production code changes begin.
No production implementation has been performed from this plan.

## Overview

Update the existing objective research dataset contract so a population names
the exact immutable FeatureSnapshot selected for each market and evaluation
cutoff. Keep outcome selection explicit by the existing market, cutoff, horizon,
and outcome fingerprint contract. Add an immutable per-experiment feature
requirements value object and apply it after schema validation.

The implementation remains inside `market-intelligence`. It will not add a
database migration, mutate historical artifacts, change FeatureProjectionV1, or
execute Q1 economics.

## Contract Changes

### `ResearchFeatureSnapshotKey`

Add an immutable key under `domain.research` containing:

- market ID;
- evaluation cutoff.

This key is horizon-independent because one FeatureSnapshot may be reused for
multiple objective outcome horizons. It must validate non-null values and provide
deterministic equality and ordering material.

### `ResearchFeatureRequirements`

Add an immutable value object under `domain.research` containing:

- a requirements version;
- a canonical immutable set of required FeatureProjectionV1 feature names;
- the missingness statuses accepted for required features.

The constructor must reject blank names, duplicate semantic names after
canonicalization, and feature names that are not present in the declared schema
when the schema is available at validation time. The empty required-name set is
valid and represents Q1's directional control.

The existing population-level `acceptedFeatureMissingness` field remains part of
the compatibility contract during this Story. Required-feature eligibility is
authoritative for experiment-specific checks; non-required feature missingness
does not exclude a schema-valid sample.

### `ResearchPopulationSpec`

Extend the immutable record with:

- `Map<ResearchFeatureSnapshotKey, String> selectedFeatureSnapshotFingerprints`;
- `ResearchFeatureRequirements featureRequirements`.

The selected snapshot map must be defensively copied and reject null keys and
blank fingerprints. The existing selected outcome map remains unchanged.

Population identity material must include sorted snapshot selections and the
requirements version, required feature names, and accepted required-feature
missingness. No database UUID, creation time, or latest-version rule may enter
semantic identity.

## Dataset Builder

Update `ResearchDatasetBuilder` as follows:

1. Validate the declared FeatureProjectionV1 schema and feature requirements.
2. Index the supplied snapshots by semantic identity and content fingerprint.
3. Resolve only the FeatureSnapshot fingerprints selected by the population.
4. Reject a missing selected snapshot instead of selecting another snapshot at
   the same market and cutoff.
5. Reject conflicting supplied artifacts for one selected immutable identity.
6. Build one sample per selected snapshot and requested outcome horizon.
7. Preserve exact outcome fingerprint selection and fail-closed behavior.
8. Validate schema shape for all FeatureProjectionV1 definitions.
9. Check only required feature names for experiment eligibility, accepting
   `PRESENT` or the requirements object's declared missingness statuses.
10. Preserve existing exclusion reasons where they remain semantically valid;
    add a dedicated deterministic reason only if the current enum cannot
    distinguish a required-feature failure from legacy global missingness.
11. Include selected snapshot identity and feature-requirement material in the
    dataset fingerprint.

The builder must not derive a selected snapshot from list order, creation time,
market plus cutoff alone, or a latest-row query.

## Manifest and Export

Extend `DatasetManifest` through its population contract rather than introducing
a second objective manifest type.

Update `ResearchDatasetBundleExporter` to serialize, in deterministic order:

- selected FeatureSnapshot identities keyed by market and cutoff;
- selected outcome fingerprints;
- feature requirements version;
- sorted required feature names;
- sorted accepted required-feature missingness.

The existing dataset fingerprint, fingerprint material, sample checksum, and
atomic bundle export behavior remain authoritative. Export field order must be
explicit and stable.

The committed cross-language fixture must only be updated if the manifest schema
change requires an expected contract update. Sample payload semantics and
historical research facts must remain unchanged.

## Runtime and Persistence Boundary

No database migration is planned. Existing `FeatureSnapshotRepository` exact
fingerprint lookup is sufficient for callers that resolve persisted artifacts.
The builder may receive a bounded candidate list, but it must use the population's
explicit semantic selection when constructing the dataset.

If implementation discovers that persisted population specifications or existing
manifests must be migrated, stop and request architectural review rather than
adding an unapproved compatibility or backfill path.

## Tests

Extend `ResearchDatasetBuilderTest` with focused coverage for:

1. explicit snapshot selection produces one stable sample per selected snapshot;
2. a later snapshot at the same market and cutoff is ignored;
3. reordered snapshot inputs do not change the dataset fingerprint;
4. a missing selected snapshot fails closed;
5. conflicting selected snapshot identities fail closed;
6. snapshot selections appear in fingerprint material and exported manifest;
7. empty Q1 feature requirements accept schema-valid snapshots with non-required
   missing values;
8. required `PRESENT` features remain eligible;
9. an unavailable required feature produces the deterministic exclusion reason;
10. accepted required-feature missingness is honored only for required features;
11. unknown or blank required feature names are rejected;
12. feature requirements and snapshot selections are order-independent;
13. adding an outcome version without changing the selected fingerprint does not
    change a frozen population;
14. missing selected outcomes remain fail-closed;
15. manifest and bundle output remain deterministic.

Extend `ExactFeatureSnapshotResolverTest` only where the changed contract
requires a new selection object or identity assertion. Preserve its guarantee
that exact lookup never falls back to population lookup.

Update existing population test fixtures and the committed cross-language
fixture only for the additive manifest fields required by this Story.

## Validation Sequence

1. `git diff --check`.
2. Focused research tests:
   `mvn -f market-intelligence/pom.xml -Dtest=ResearchDatasetBuilderTest,ExactFeatureSnapshotResolverTest test`.
3. Full `market-intelligence` verification using the repository-standard Maven
   command.
4. Confirm all existing objective dataset and cross-language fixture tests pass.
5. Confirm no Q1 economic evaluation or historical data mutation is performed.
6. Review the complete diff and verify only Story 0131 artifacts and approved
   source/test changes are present.

## Stop Conditions

Implementation must stop for human review if it requires:

- a database migration or historical backfill;
- changes to FeatureProjectionV1 or its 43-feature schema;
- changes to Order Book, fee, funding, execution, or P&L semantics;
- a new public API or cross-service contract;
- a replacement for semantic fingerprint identity;
- selecting a latest or arbitrary snapshot for compatibility;
- changing existing historical dataset facts rather than only extending the
  manifest contract.

## Expected Outcome

After implementation, the same explicit population specification must produce the
same selected snapshot/outcome identities, sample fingerprints, dataset
fingerprint, and manifest semantics even when additional immutable artifacts are
persisted later. Q1 must be representable with zero required domain features
without weakening schema validity or objective outcome selection.
