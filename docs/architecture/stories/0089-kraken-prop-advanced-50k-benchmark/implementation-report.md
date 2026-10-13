# Implementation Report

## Scope

Implemented Story 0089 as a persisted, provider-neutral Kraken Prop Advanced 50k benchmark catalog entry. No Kraken-specific runtime Risk branch, broker adapter, funding model, leverage model, or automatic provider terminal action was added.

## Changes

- Added Flyway migration `V30__seed_kraken_prop_advanced_50k_benchmark.sql`.
- Seeded versioned Risk Profile `KRAKEN_PROP_ADVANCED_50K_RISK` with generic position, exposure, daily drawdown, and maximum total drawdown rules.
- Seeded versioned ChallengeDefinition `KRAKEN_PROP / EVALUATION / ADVANCED`, USD 50,000, 9% EQUITY target, CASH settlement, and `LINEAR_PERPETUAL` compatibility.
- Preserved official-source URLs, retrieval timestamp, modeled rules, and explicitly excluded fee/funding/time-limit behavior in provenance.
- Added integration coverage for catalog persistence, account provisioning, UTC 00:30 daily reset, Challenge start, instrument compatibility, target progression, and inclusive MDL/MDD breaches.
- Adjusted the existing Challenge persistence assertion to account for persisted catalog definitions.
- Added H2-compatible constraint normalization in V30 so `MAX_TOTAL_DRAWDOWN` can be seeded across test and production database naming behavior.

## Validation

- `mvn -q -Dtest=KrakenPropAdvanced50kBenchmarkTest test` in `trading-core`: passed after clean compilation.
- `mvn -q -Dtest=ChallengePersistenceTest,KrakenPropAdvanced50kBenchmarkTest test` in `trading-core`: passed.
- `mvn -q verify` in `trading-core`: passed after the persistence assertion adjustment.
- `mvn -q verify` in `risk-domain`: passed.
- `git diff --check`: passed.
- Benchmark test against PostgreSQL 16 with the existing V25 schema and V26-V30 upgrade path: passed.
- Benchmark test against PostgreSQL 16 with the fresh common migration set: passed.

Existing Mockito, H2/Flyway, LoadBalancer, and Spring Boot warnings remain non-blocking.

The full PostgreSQL fresh-install path could not be validated because the
pre-existing vendor migration `postgresql/V3` references `risk_evaluation_t1`
before common migration V9 creates it. That historical migration ordering is
outside Story 0089 scope and was not changed.

## Exclusions

- No provider-specific runtime logic or Kraken adapter changes.
- No funding, commission, leverage, margin, fee, or automatic provider terminal-state modeling.
- No Git commit, push, merge, or branch integration.
