# Implementation Plan

## 1. Catalog

- Extend `ChallengeDefinitionRepository` with a deterministic ordered read.
- Add `ChallengeCatalogService` to select one current definition per logical
  definition ID using `effectiveFrom`, then `definitionVersion` as a tie-break.
- Add a catalog response DTO that exposes product configuration and provenance,
  never the JPA entity.
- Add `GET /api/v1/challenges` requiring the authenticated principal.

## 2. Lifecycle API

- Add `POST /api/v1/accounts/{accountId}/challenge` with a validated request
  containing only the selected definition ID and version.
- Pass the authenticated `UserDto.userId` to `StartChallengeService`.
- Return a stable Challenge instance response including definition and Risk
  Policy references, capital, status, timestamps, and terminal metadata.
- Keep the existing progress endpoint and derived projection unchanged in
  shape.

## 3. Error Mapping

- Introduce typed Challenge application exceptions that retain the existing
  base exception categories for compatibility with current unit tests.
- Map resource/ownership failures to `404`, active-instance conflicts to `409`,
  request/compatibility failures to `400`, and missing server Risk Policy
  configuration to `500` through a Challenge-scoped `ProblemDetail` handler.
- Convert progress lookup failures to the same deterministic boundary.

## 4. Verification

- Add unit tests for current-version selection and same-timestamp tie-breaking.
- Add controller tests for catalog/start response and validation/error mapping
  where the existing MVC test conventions support it.
- Extend the Kraken benchmark integration test to verify catalog discovery and
  the API-facing start path through the application service boundary.
- Run Challenge unit/integration tests, the Trading Core test suite, and
  `git diff --check`.

## Out of Scope

- New lifecycle transitions, reset/restart, funding, leverage, liquidation,
  exact risk-day reconstruction, or Angular routes.
- Provider-specific branches or changes to Broker Account provisioning.
