# Implementation Report

## Implemented

- Added authenticated `GET /api/v1/challenges`.
- Added authenticated `POST /api/v1/accounts/{accountId}/challenge`.
- Added DTOs for catalog definitions, start requests, and Challenge instances.
- Added generic current-definition selection using `effectiveFrom` and a
  deterministic version tie-breaker.
- Reused `StartChallengeService` for ownership, active-instance, capital,
  currency, settlement, and Risk Policy validation.
- Added Challenge-scoped typed exceptions and `ProblemDetail` mappings.
- Preserved the existing derived progress endpoint and transition authorities.
- Added catalog, controller, and Kraken benchmark coverage.

## Files

- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/api/`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeCatalogService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/Challenge*Exception.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/StartChallengeService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeProgressService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeDefinitionRepository.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/challenge/`

## Validation

- `mvn -q -DskipTests compile`: passed.
- Targeted Challenge tests: passed.
- `mvn -q -Dtest=KrakenPropAdvanced50kBenchmarkTest test`: passed.
- `mvn -q test`: completed with 129 Surefire report files and no reported
  non-zero failure/error entries.
- `mvn -q verify`: completed; repository reports contain no non-zero failure or
  error entries.
- `git diff --check`: passed.

The build emitted existing warnings for H2/Flyway version verification,
explicit Hibernate dialect configuration, Spring Cloud LoadBalancer cache, and
Mockito dynamic agent attachment. These are not introduced by Story 0091.
