# Implementation Report - Story 0131

## Outcome

Implemented the approved Story 0131 objective research contract changes in
`market-intelligence`.

Research populations now select exact immutable FeatureSnapshot fingerprints by
market and evaluation cutoff. The dataset builder no longer consumes every
snapshot found in a cutoff range. A later immutable snapshot version is ignored
unless explicitly selected, and a missing selected snapshot fails closed.

Experiment feature eligibility is now represented by an immutable
`ResearchFeatureRequirements` value object. Schema validity remains mandatory,
while missingness on non-required features no longer excludes a sample. Q1 can
declare zero required domain features.

## Changes

- Added `ResearchFeatureSnapshotKey`.
- Added `ResearchFeatureRequirements` with Q1 empty requirements.
- Extended `ResearchPopulationSpec` with explicit snapshot selections and feature requirements.
- Updated `ResearchDatasetBuilder` to resolve only selected snapshot fingerprints.
- Added fail-closed selected snapshot identity validation.
- Applied experiment-specific required-feature missingness checks.
- Added selected snapshot and feature requirement identity material to dataset fingerprints.
- Extended dataset bundle manifest serialization with snapshot selections and feature requirements.
- Updated export command parsing for the new population contract.
- Added reproducibility, missing-identity, non-required-missingness, and required-feature tests.
- Updated Story status to implementation complete and review approved.

## Validation

Passed:

```text
mvn -f market-intelligence/pom.xml -Dtest=ResearchDatasetBuilderTest,ResearchDatasetExportCommandTest,ExactFeatureSnapshotResolverTest test
26 tests, 0 failures

mvn -f market-intelligence/pom.xml test
full module suite passed

git diff --check
```

The full Maven run emitted pre-existing test/runtime warnings from Spring,
Mockito, H2 uniqueness races, and unavailable service mocks; all tests passed.

## Scope Verification

- No database migration was added.
- `FeatureProjectionV1` was not changed.
- No historical research data was mutated.
- No Q1 economic evaluation was executed.
- No commit, push, or merge was performed.
- The pre-existing untracked functional audit report was preserved.

## Remaining Gate

The implementation is complete and approved by code review. No commit, push, or
merge was performed.
