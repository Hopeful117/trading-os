# Story 0094 - Repository Analysis

## Current State

- Commit investigated: `42d328c2` on `main`.
- The worktree already contains the untracked validation report from Story
  0093: `docs/architecture/stories/0093-postgresql-fresh-install-migration-integrity/e2e-validation-report.md`.
- DevLog context was attempted for `trading-os` but was unavailable because the
  provider returned `429 credit_balance_exhausted`. Repository evidence is
  authoritative for this analysis.

## Confirmed Blocker

`GatewayRouteConfig` has no route for `/api/v1/challenges/**` and no dedicated
route for `/api/v1/accounts/{accountId}/challenge/**`. Consequently, the
catalog request returns `404` through Gateway while the same request returns
`200` directly from Trading Core, as recorded by the Story 0093 E2E report.

## Public Challenge Endpoints

Trading Core exposes the following public endpoints:

| Method | Path | Controller | Required by Angular |
| --- | --- | --- | --- |
| `GET` | `/api/v1/challenges` | `ChallengeLifecycleController` | `ChallengeService.findCatalog()` |
| `POST` | `/api/v1/accounts/{accountId}/challenge` | `ChallengeLifecycleController` | `ChallengeService.startChallenge()` |
| `GET` | `/api/v1/accounts/{accountId}/challenge` | `ChallengeProgressController` | `ChallengeService.findProgress()` |

`StartChallengeRequest` contains the backend-selected `definitionId` and
`definitionVersion`. No other public Challenge controller endpoint exists.
Internal endpoints are not part of this public surface.

## Existing Gateway Conventions

- `GatewayRouteConfig` is the single route table.
- Public routes preserve the complete `/api/v1/...` path without rewriting.
- Trading Core routes use `lb://trading-core`, optionally overridden through
  `gateway.targets.trading-core` for integration tests.
- Route predicates are path-family based and are ordered explicitly in the
  builder.
- The existing `/api/v1/accounts/**` route also targets Trading Core and does
  not currently match the Challenge endpoint in the observed runtime; an
  explicit Challenge route will make the intended public contract and
  precedence testable.
- Broker credential command routes are more specific and target Broker Service;
  the Challenge route must not overlap those paths.
- `/internal/**` is not routed by `GatewayRouteConfig`.

## Authentication Boundary

- Gateway permits only registration, login, Swagger, and API documentation
  paths anonymously. All Challenge paths remain authenticated through the
  existing `anyExchange().authenticated()` rule.
- Gateway's JWT filter validates the bearer token and establishes the
  authenticated principal. It does not need Challenge-specific changes.
- Trading Core receives the bearer authorization header through the existing
  Gateway proxy behavior and derives owner identity from its own authenticated
  context.
- Challenge ownership, account compatibility, admission validation, and
  lifecycle behavior remain in Trading Core services.

## Route Design

Add two explicit Trading Core routes:

1. `challenge-lifecycle` for `/api/v1/challenges/**`.
2. `challenge-account` for `/api/v1/accounts/*/challenge/**`.

Both routes use `uriFor.apply("trading-core", "lb://trading-core")` and no path
rewrite. The account-specific route should be placed before the broad
`/api/v1/accounts/**` route so precedence is explicit and regression-tested.

The account route uses the existing Gateway path pattern style and is limited
to one account path segment followed by `challenge`, preventing unrelated
account APIs and internal paths from matching.

## Test Strategy

Extend the existing Gateway route-level tests to verify:

- both Challenge route IDs exist and target `lb://trading-core`;
- the catalogue route matches `GET /api/v1/challenges`;
- the account route matches both `POST` and `GET`
  `/api/v1/accounts/{accountId}/challenge`;
- the account route does not match unrelated account paths;
- `/internal/...` and unrelated Challenge-like paths are not exposed;
- the real Gateway downstream integration forwards all three requests to the
  Trading Core stub and preserves authorization for authenticated requests.

The existing integration test already proves real HTTP routing and JWT
authorization propagation through Gateway and is the strongest fit for the
runtime route regression.

## Validation Plan

After implementation approval:

- run the Gateway test module, including route and downstream integration tests;
- run `git diff --check`;
- start the isolated runtime using the current checkout/image;
- authenticate through Gateway;
- call catalog, start, and progress through Gateway only for acceptance;
- compare directly with Trading Core only if diagnosing a failure;
- preserve the scheduler SQL typing error as a secondary observation and do not
  modify it.

## Scope and Risks

- No Challenge, Risk, settlement, Market Data, Angular, or scheduler behavior
  changes are required.
- The only implementation surface should be `GatewayRouteConfig` plus Gateway
  route regression tests and Story documentation.
- Existing Story 0093 evidence must remain unchanged; any later successful
  validation should be recorded separately.
