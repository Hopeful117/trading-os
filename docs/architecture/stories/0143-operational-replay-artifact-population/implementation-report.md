# Implementation Report - Story 0143

## Implemented

- Added disabled-by-default artifact population configuration to the research
  lifecycle:
  - enablement;
  - replay depth;
  - maximum attempts per lifecycle run;
  - promotion reason.
- Added `ResearchArtifactPopulationService` in Market Intelligence.
- Population uses the aligned research cutoff and deterministic
  `OrderBookReplayIdentity`.
- Population explicitly requests canonical replay without a semantic identity
  before promotion.
- Only complete, trusted, cutoff-bounded, market/depth-matching replay responses
  are promoted.
- Existing successful identities are skipped in-process; the configured per-run
  limit applies to canonical attempts and does not prevent later configured
  markets from being selected.
- Promotion and canonical replay failures are isolated and logged without stopping
  the research lifecycle.
- Added the promotion operation to the existing retention Feign client without
  removing its dependency protection/release operations.
- Added Docker Compose environment bindings for the new operational controls.

## Changed Files

- `docker-compose.yml`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/OrderBookRetentionClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchArtifactPopulationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchLifecycleService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/ResearchLifecycleProperties.java`
- `market-intelligence/src/main/resources/application.properties`
- focused Market Intelligence tests

## Validation

- `mvn -q -Dtest=ResearchArtifactPopulationServiceTest,ResearchLifecyclePropertiesTest,ResearchLifecycleServiceTest test`: passed.
- `mvn test -q` in `market-intelligence`: passed.
- `git diff --check`: passed.
- Runtime validation was not completed because `docker compose ps` cannot
  interpolate the existing runtime configuration without the required
  `ORDER_BOOK_ARTIFACT_SIGNING_KEY` environment variable. No secret was inspected
  or invented.

## Remaining Work

- Run the authenticated `/research` before/after population measurement in an
  environment with the configured signing key and market IDs.
- Verify actual artifact inventory, reuse, fallback, invalidation, and p50/p95
  runtime behavior.
- Perform independent code review and human approval.
- Do not enable population by default until the runtime operational measurement
  is accepted.
