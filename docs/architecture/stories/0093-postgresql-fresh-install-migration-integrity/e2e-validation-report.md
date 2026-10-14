# E2E PAPER Challenge Validation Report

Date: 2026-10-13
Commit tested: `42d328c2` (`main`)

## Scope

The first coherent PAPER Challenge workflow was exercised through the public
Gateway using an isolated Docker environment and a fresh Trading Core
PostgreSQL volume. The run stopped at the first meaningful product blocker.

## Environment Validation

- Trading Core was rebuilt from the current checkout before the clean run.
- Fresh PostgreSQL migrations completed successfully through V31.
- The isolated Broker Service started with a valid temporary Base64 master key.
- No application source code or existing developer containers were modified.
- DevLog context was unavailable because the provider returned
  `429 credit_balance_exhausted`.

## Workflow Evidence

1. `POST /api/v1/users/register` through Gateway: `200 OK`.
2. `POST /api/v1/users/login` through Gateway: `200 OK`, JWT issued.
3. `GET /api/v1/risk-profiles/eligible` through Gateway: `200 OK`.
4. `POST /api/v1/broker-accounts` through Gateway with the Kraken Advanced
   50K risk profile and `PAPER` execution mode: `201 Created`.
5. `GET /api/v1/challenges` through Gateway: `404 Not Found`.
6. `GET /api/v1/challenges` directly against Trading Core: `200 OK`, returning
   the Kraken Prop Advanced 50K definition.

## First Product Blocker

The public Gateway does not route `/api/v1/challenges/**` to Trading Core.
Trading Core exposes `GET /api/v1/challenges` from
`ChallengeLifecycleController`, but `GatewayRouteConfig` has no corresponding
challenge route. The normal public workflow cannot discover a Challenge, so it
cannot proceed to Challenge start, TradePlan creation, Risk evaluation, or
PAPER execution through the supported boundary.

## Recommended Story 0094 Scope

Add and validate Gateway routing for the Challenge lifecycle and progress
endpoints exposed by Trading Core, without changing Challenge domain behavior
or bypassing the Gateway.

## Secondary Observations

- The active Challenge monitoring scheduler logs a PostgreSQL parameter typing
  error while no Challenge exists. This did not block the first workflow step,
  but should be investigated separately.
- Initial temporary environment failures involved invalid Base64 secrets,
  stale Docker images, and split Compose networks. These were isolated test
  setup issues, not application changes.
