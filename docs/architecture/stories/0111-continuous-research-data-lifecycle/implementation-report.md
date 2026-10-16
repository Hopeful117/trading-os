# Story 0111 - Continuous Research Data Lifecycle

## Implementation

Market Intelligence now has a disabled-by-default, Spring-scheduled research
lifecycle. When explicitly configured, it:

```text
aligned UTC cutoff
    -> STRICT_OBSERVED analysis execution
    -> persisted FeatureSnapshot projection
    -> matured ObservedMarketOutcome evaluation
```

The lifecycle reuses the existing analysis, projection, and outcome services.
It does not calculate new market facts, alter evidence, or export datasets.

## Configuration

The lifecycle is configured with typed properties under `research.lifecycle`:

- `enabled`
- `market-ids`
- `observation-cadence` (default `PT1H`)
- `outcome-horizons` (default `PT1H,PT4H,PT24H`)
- `fixed-delay` (default `PT1M`)

Enabling the lifecycle without market IDs, with a non-positive cadence or fixed
delay, or with duplicate/non-positive horizons fails validation.

The existing Market Data capture scheduler remains independently configured. Both
services must be explicitly configured for the same bounded market universe.

## Semantics

- Cutoffs are aligned to UTC epoch boundaries using the configured cadence.
- Only `STRICT_OBSERVED` analysis executions are submitted.
- Missed historical cutoffs are not fabricated on restart; the next aligned cutoff
  is used.
- Execution scope is persisted in PostgreSQL so completed executions can be
  projected after a restart.
- Snapshot persistence remains identity/fingerprint idempotent.
- Outcomes are evaluated only when `now >= T0 + H`.
- Outcome persistence remains immutable and fingerprint based.
- Incomplete or unavailable outcomes remain visible through existing status
  semantics and are not replaced with invented values.

## Readiness

`GET /internal/v1/research/readiness` exposes configured markets, latest readable
evidence timestamps, snapshot counts and ranges, outcome counts by horizon/status,
maturing outcomes, and an approximate available research population.

Dataset export remains an explicit `ResearchDatasetExportCommand` action. The
continuously growing factual population is not itself a dataset.

## Architecture Compatibility

The implementation preserves ADR-057 through ADR-062. Existing historical rows
are not modified, no historical backfill mode is introduced, no ResearchSample
table is added, and Python remains downstream validation/ML authority only.

## Validation

- `mvn -f market-intelligence/pom.xml -DskipTests compile`
- `mvn -q -Dtest=ResearchLifecycleServiceTest,ResearchLifecyclePropertiesTest test`
  - passed.
- Persistence coverage now includes V16 migration startup, strict scope
  rehydration, and concurrent idempotent execution insertion.
- Readiness metrics are restricted to the configured outcome horizons.
- `mvn -q verify -DskipTests=false` - passed.
- `git diff --check`
- SonarQube analysis was executed. The Quality Gate remains failed with 77.6%
  new-code coverage and 65 new violations; duplicated new lines passed.

Runtime validation is limited to inspection of the existing local services and
database. The lifecycle remains disabled by default, so no research facts are
created implicitly during application startup.

## Documentation Outcome

Documentation update: required and completed. The Story artifact set and
operational runbook document configuration, restart behavior, disablement,
validation, and known limitations.

## Vault Outcome

The Obsidian vault was not consulted. The repository and accepted ADRs provided
the relevant authoritative context. No vault action is proposed.
