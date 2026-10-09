# Story 0073 - Implementation Report

## Result

Implemented the provider-neutral News Service foundation and optional Market
Intelligence integration.

## Delivered

- Added the standalone `news-service` Spring Boot service.
- Added PostgreSQL/Flyway persistence for economic events and financial news.
- Added deterministic source-based identities and normalized domain models.
- Added bounded authenticated read APIs:
  - `GET /api/v1/news/events`
  - `GET /api/v1/news/items`
- Read APIs now expose explicit availability status, fetch time, message, and
  items instead of an ambiguous raw list.
- Added authenticated internal context API:
  - `GET /internal/v1/news/context/{marketId}`
- Added user JWT and inter-service JWT security boundaries.
- Added the Market Intelligence `NEWS` context contributor and provider-neutral
  Feign client.
- Added the Gateway route for `/api/v1/news/**`.
- Added Docker Compose service, database, volume, and service JWT wiring.
- Added deterministic fixture synchronization tests without external credentials.
- Added JPA reload/query-bound tests, public contract tests, query-window tests,
  and service-JWT validation tests.
- Kept production provider integration disabled by default with an explicit
  `UNAVAILABLE` context result.
- Added explicit provider availability propagation for `UNSUPPORTED` and
  `INCOMPLETE` states through News Service and Market Intelligence.
- Replaced delimiter-ambiguous source identities with length-prefixed identity
  inputs and added collision-boundary tests.
- Preserved event and news provenance fields in the Market Intelligence NEWS
  context contract.
- Made public economic-event attribution configuration-driven by normalized
  `sourceName` rather than hardcoded to one provider.
- Updated the production analysis coordinator to consume each strategy's full
  baseline context requirements, including the active strategy's optional
  `NEWS` section.
- Rejected blank source names and source identifiers before deterministic ID
  generation.

## Validation

Executed successfully:

- `mvn test -q` in `news-service`.
- `mvn verify -q` in `news-service`.
- `mvn test -q` in `market-intelligence`.
- `mvn test -q` in `gateway`.
- `docker compose config --quiet` with non-secret test environment values.
- `git diff --check`.

Additional correction validation:

- `mvn test` in `news-service`: 40 tests passed.
- `mvn test` in `market-intelligence`: 468 tests passed.
- `git diff --check` after the correction set.
- The production coordinator requirement test verifies that the active
  strategy's `NEWS` context is requested.

## Limits

- No production news or economic-calendar provider has been selected or
  integrated.
- No external network, provider credentials, quotas, or synchronization
  scheduler are included.
- No frontend news presentation is included.
- Live News data remains unavailable until a later provider integration Story
  enables `NEWS_PROVIDER_ENABLED` and supplies a `NewsSourcePort` adapter.

## Documentation Reconciliation

The implementation report records the correction set. Runtime configuration now
contains provider attribution mappings, and the provider-neutral contracts now
carry explicit availability and provenance states.

## Vault Outcome

The vault was not consulted because this correction set concerns repository
contracts and service boundaries only. No vault action is proposed.
