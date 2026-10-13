# Story 0094 - Implementation Plan

## Objective

Expose the three existing public Challenge endpoints through the Gateway while
preserving Gateway authentication, bearer propagation, service discovery, and
Trading Core ownership authorization.

## Changes

### 1. Gateway route configuration

Update `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`:

- Add a `challenge-lifecycle` route matching `/api/v1/challenges/**`.
- Add a `challenge-account` route matching
  `/api/v1/accounts/*/challenge/**`.
- Send both routes to
  `uriFor.apply("trading-core", "lb://trading-core")`.
- Preserve paths without rewrite or custom filters.
- Place the account-specific route before the broad `accounts` route so the
  intended precedence is explicit.
- Do not add any `/internal/**` route.

### 2. Route-level regression tests

Extend `GatewayExecutionRouteTest` or add a focused Gateway route test to verify:

- route IDs and `lb://trading-core` targets;
- catalogue matching;
- account Challenge `POST` matching;
- account Challenge `GET` matching;
- unrelated account paths do not match the Challenge route;
- internal Challenge paths are not matched;
- existing route registration remains intact.

### 3. Real Gateway downstream test

Extend `GatewayDownstreamRoutingIntegrationTest` with authenticated requests
for all three public endpoints. The existing Trading Core stub will record the
method and path and verify the authorization header is present. This proves
the production route table, service target override, proxy path preservation,
and JWT propagation together.

Add an unauthenticated request assertion for a Challenge path if the existing
test harness can exercise the Gateway security chain without weakening test
configuration. The expected result is rejection before downstream forwarding.

### 4. Documentation

After implementation, add an implementation report under the Story directory
covering changed files, tests, runtime validation, and the explicit statement
that the scheduler observation remains out of scope. Preserve the original
Story 0093 E2E evidence unchanged.

## Validation

Run:

```text
mvn -pl gateway test
git diff --check
```

Then rebuild/restart only the isolated Gateway runtime as needed and execute:

1. Gateway registration/login.
2. Authenticated `GET /api/v1/challenges` through Gateway.
3. Authenticated `POST /api/v1/accounts/{accountId}/challenge` through
   Gateway using the seeded definition ID/version.
4. Authenticated `GET /api/v1/accounts/{accountId}/challenge` through Gateway.
5. An unauthenticated Challenge request to verify rejection.

The runtime checks must not call Trading Core directly for acceptance, except
for diagnostic comparison if Gateway behavior fails.

## Risks and Mitigations

- Broad account route precedence: cover it with explicit route predicate tests.
- Accidental internal exposure: assert `/internal/**` is not matched and keep
  route patterns constrained to public prefixes.
- Missing bearer propagation: assert the downstream stub receives
  `Authorization: Bearer ...`.
- Existing scheduler SQL typing error: record it as a remaining observation;
  do not modify scheduler or Challenge domain code.

## Expected Files

- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`
- `gateway/src/test/java/com/hope/trading/gateway/GatewayExecutionRouteTest.java`
  or a focused Challenge route test
- `gateway/src/test/java/com/hope/trading/gateway/GatewayDownstreamRoutingIntegrationTest.java`
- `docs/architecture/stories/0094-challenge-gateway-routing/implementation-report.md`
