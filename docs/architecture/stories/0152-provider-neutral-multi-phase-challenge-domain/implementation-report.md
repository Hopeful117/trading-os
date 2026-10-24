# Story 0152 - Implementation Report

## Status

`IMPLEMENTED - AWAITING HUMAN REVIEW`

## Changes

- Added immutable provider-neutral `ChallengePhaseDefinition` values with
  ordered phase keys, phase-specific profit targets, progression sources,
  minimum trading days, Risk policy references, and optional provider
  confirmation.
- Added `ExecutedTradingEvidence` and `ChallengeProgressionEvidence` with
  explicit incomplete/contradictory handling and opaque confirmation evidence.
- Extended `ChallengeDefinition` with an explicit immutable phase collection and
  a deterministic implicit legacy phase view.
- Extended `ChallengeInstance` with current phase, active Risk policy, local
  satisfaction, provider confirmation, and phase transition state while keeping
  legacy terminal statuses and nullable legacy phase fields.
- Generalized Challenge evaluation to aggregate phase objectives, enforce phase
  ordering, preserve Risk-breach-first behavior, and fail closed on unavailable
  trading evidence.
- Changed account monitoring to load the active phase Risk policy without moving
  Risk calculations into Challenge.
- Added additive Flyway migration V35 for phase definitions and nullable phase
  instance state.
- Added unit and persistence coverage for phase completion, ordering, minimum
  trading days, provider confirmation, invalid evidence, persistence, and
  migration history.

## Scope Verification

- No FTMO-specific Challenge type or rule was added.
- No cTrader dependency, execution path, or Spotware runtime validation was
  added.
- Existing single-phase factories and lifecycle behavior remain supported.
- Existing database rows are not backfilled with phase history or provider
  confirmation.
- Static drawdown and daily drawdown remain Risk-domain responsibilities.

## Validation

Executed successfully:

```text
mvn -q -DskipTests compile
mvn -q -Dtest=ChallengeDefinitionTest,ChallengeInstanceTest,ChallengeEvaluatorTest,ChallengeMultiPhaseEvaluatorTest test
mvn -q -Dtest=ChallengePersistenceTest,ChallengeCatalogServiceTest,KrakenPropAdvanced50kBenchmarkTest test
mvn -q -Dtest=PostgresFreshInstallMigrationTest test
mvn -q test
git diff --check
```

The full Trading Core suite completed successfully. Expected warnings from
Mockito, H2 version compatibility, and existing concurrency/negative migration
tests were observed; no test failure remained.

## Documentation Reconciliation

Updated the canonical Story artifacts and accepted ADR-075. No frontend,
provider runtime, cTrader, or FTMO-specific documentation required updating.

## Vault Outcome

The Obsidian vault was not consulted because the repository ADR, Story, source,
and tests were sufficient and authoritative for this local domain change. No
vault action is proposed.
