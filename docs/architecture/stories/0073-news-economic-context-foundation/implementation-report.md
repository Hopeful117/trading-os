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

## Validation

Executed successfully:

- `mvn test -q` in `news-service`.
- `mvn verify -q` in `news-service`.
- `mvn test -q` in `market-intelligence`.
- `mvn test -q` in `gateway`.
- `docker compose config --quiet` with non-secret test environment values.
- `git diff --check`.

## Limits

- No production news or economic-calendar provider has been selected or
  integrated.
- No external network, provider credentials, quotas, or synchronization
  scheduler are included.
- No frontend news presentation is included.
- Live News data remains unavailable until a later provider integration Story
  enables `NEWS_PROVIDER_ENABLED` and supplies a `NewsSourcePort` adapter.
