# Story 0107C-0 - Implementation Report

## Implementation Status

Implementation is complete and remains uncommitted for human review.

## Delivered

- Added provider-neutral `HistoricalOhlcRange`, `HistoricalOhlcGap`, and range
  status types.
- Added the `CAPTURED` evidence policy while preserving the existing
  `STRICT_OBSERVED` as-of policy.
- Added bounded range validation with a maximum of 720 expected candles.
- Added epoch-aligned expected-candle calculation using `OhlcInterval.duration`.
- Added explicit coverage counts, coverage bounds, gaps, and
  `AVAILABLE`/`INCOMPLETE`/`UNAVAILABLE` semantics.
- Added deterministic aggregate evidence fingerprinting.
- Applied deterministic correction selection in both the SQL adapter and the
  range calculator using `knownAt DESC`, `capturedAt DESC`, and
  `observationId DESC`.
- Added a repository-first native CAPTURED range query using existing correction
  ordering.
- Added `GET /internal/v1/historical-ohlc/{marketId}/range`.
- Preserved provider isolation, append-only evidence, and the existing as-of
  endpoint.
- Added unit, controller, persistence, correction, backfill-isolation, boundary,
  filtering, policy-validation, fingerprint, and repeatability tests.
- Configured the H2 test profile to use UTC so `Instant` range boundaries match
  PostgreSQL semantics.

## Scope Verification

No outcome, return, maturity, label, strategy, Risk, execution, broker,
Market-Intelligence, Gateway, frontend, provider fallback, or backfill behavior
was added.

No database migration was required. The existing historical evidence schema and
indexes contain the required fields.

## Validation

Passed:

- `mvn -q -Dtest=HistoricalOhlcRangeCalculatorTest,InternalHistoricalOhlcControllerTest,HistoricalOhlcEvidencePersistenceTest,HistoricalOhlcHistoryServiceTest test`
- `mvn -q test` from `market-data`.
- `mvn -q verify` from `market-data`, including the 80% JaCoCo threshold.
- PostgreSQL 16 persistence test against an isolated temporary database:
  `HistoricalOhlcEvidencePersistenceTest`, 7 tests, 0 failures/errors.
- `git diff --check`.
- Targeted SonarQube analysis for `trading-os:market-data`.
- SonarQube Quality Gate: `OK`.
- SonarQube new coverage: `81.1%`.
- SonarQube new duplicated lines density: `0.0%`.
- SonarQube new violations: `0`.
- SonarQube new security hotspots: `0`.

The repository-wide `scripts/quality-scan.sh` was started but exceeded the
120-second execution limit while progressing into `market-intelligence`. It had
already completed the Market Data verification successfully with 204 tests and
the coverage check passing. The targeted Market Data Sonar scan completed
successfully afterward.

The first PostgreSQL attempt against the long-lived application database hit
fixture-market uniqueness conflicts from pre-existing data. The validation was
then repeated against an isolated temporary PostgreSQL database and passed; the
temporary database was removed automatically afterward.

## Documentation Reconciliation

The Story, repository analysis, implementation plan, and this implementation
report are the relevant canonical artifacts. No additional architecture or
operational documentation update was required because the internal contract is
fully defined by Story 0107C-0 and remains governed by ADR-057 and ADR-058.

No new ADR was created.

## Vault Outcome

The Obsidian vault was not consulted because the implementation was governed by
the approved Story, repository source, tests, and accepted ADRs. No vault action
is proposed.

## Remaining Human Review

- Review the complete uncommitted diff.
- Confirm the approved Story contract and internal endpoint shape.
- Commit and integrate only after human review and approval.
