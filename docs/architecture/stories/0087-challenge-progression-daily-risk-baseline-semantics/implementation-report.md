# Story 0087 - Implementation Report

## Result

Implemented provider-neutral Challenge `BALANCE`/`EQUITY` progression,
settlement compatibility validation, and authoritative per-Risk-Day balance
baseline capture while preserving Risk ownership of drawdown calculations.

## Repository Baseline

- Branch: `story/0087-challenge-progression-daily-risk-baseline-semantics`
- Starting HEAD: `f7a612c9b69f6b9a31a00a224e6febc8b213f2d2`
- No commit, push, merge, reset, or checkout performed.
- The worktree contained no pre-existing changes in the affected Story files
  before implementation.
- DevLog context was unavailable; current repository and approved artifacts
  were used as authority.

## Implemented

- Added generic `ProgressionValueSource.EQUITY`.
- Added nullable `requiredSettlementModel` to `ChallengeDefinition`.
- Added pre-persistence settlement compatibility validation when starting a
  Challenge.
- Added a shared progression-value resolver that uses only fresh authoritative
  account equity for `EQUITY` and never falls back to realized balance.
- Preserved risk-breach-before-profit-target ordering and fail-closed EQUITY
  behavior.
- Added additive Flyway migration V28 for the EQUITY constraint and nullable
  settlement requirement column.
- Changed account Risk monitoring to capture the authoritative observed balance
  for the first evaluation of each Risk Day and reuse the immutable baseline.
- Preserved static starting balance semantics for total drawdown.
- Kept Risk timezone and reset time explicit and provider-neutral through account
  configuration.
- Added/updated focused Challenge, settlement, Risk baseline, persistence, and
  regression tests, including read-projection and concurrent first-writer
  coverage.

## Documentation Outcome

Updated the Story status and workflow note to reflect that the approved plan is
now in implementation. No API, frontend, operational, or architecture
documentation outside the Story artifacts required updating.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed; the Story is
fully governed by the approved Story, plan, repository code, and accepted ADRs.

## Validation

- Focused Challenge, settlement, Risk baseline, and persistence tests passed:
  `ChallengeDefinitionTest`, `StartChallengeServiceTest`,
  `ChallengeEvaluatorTest`, `RiskPersistenceTest`, and
  `AccountRiskMonitoringServiceTest`.
- The broader `trading-core` `mvn -q verify` completed without reported
  Surefire failures in the generated reports; expected test logs include
  deliberate exception and unique-constraint scenarios.
- `git diff --check` passed.
- SonarQube analysis was not executed; no authenticated working-tree analysis
  result was available.

## Review Corrections

- Removed the account-provisioning baseline seed so the first Risk evaluation
  captures the authoritative observed balance.
- Serialized first baseline creation with an account row lock and added a
  database-backed concurrent first-writer test.
- Added read-projection coverage for fresh EQUITY, stale EQUITY without balance
  fallback, and unchanged CASH BALANCE semantics.
- Replaced newly duplicated Sonar literals with constants.

## Residual Limitations

- No frontend changes were made, as required by the Story scope.
- No commit, pull request, code review approval, or final engineering report
  has been produced yet.
