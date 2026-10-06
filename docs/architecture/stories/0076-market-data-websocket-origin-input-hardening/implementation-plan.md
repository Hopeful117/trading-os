# Implementation Plan - Story 0076

## Status

`APPROVED - EXECUTED`

## Objective

Harden the public Market Data WebSocket without changing its public-data trust
model, internal service-JWT boundary, Gateway contract, or Angular stream
behavior.

## Current Contract Inputs

The Angular frontend uses the relative Gateway endpoint `/ws/market-data` and
currently requests:

- Ticker: `symbol`, `type=TICKER`;
- OHLC: `marketId`, `symbol`, `interval`, `type=OHLC`;
- Order book: `marketId`, `symbol`, `depth`, `type=ORDER_BOOK`;
- Recent trades: `marketId`, `symbol`, `type=TRADES`.

Angular currently uses OHLC intervals `1, 5, 15, 30, 60, 240, 1440` minutes
and order-book depths `10` and `25`. These values become the compatibility
baseline for server-side validation.

## Sequence

### Phase 1 - Establish configuration and compatibility tests

1. Add a Market Data WebSocket configuration property for allowed browser
   origins, sourced from the runtime environment.
2. Define explicit test/local configuration for the property.
3. Define production-like validation so an enabled WebSocket cannot silently
   fall back to unrestricted origins.
4. Verify the Angular development/runtime origin and retain the relative
   `/ws/market-data` Gateway contract.

### Phase 2 - Implement origin policy

1. Replace `setAllowedOriginPatterns("*")` with the configured allowlist.
2. Preserve public unauthenticated market-data streaming; do not introduce user
   JWT authentication as a substitute for origin policy.
3. Keep origin policy configuration isolated to Market Data WebSocket setup.
4. Add tests for an allowed origin, a rejected origin, and missing/invalid
   production-like configuration.

### Phase 3 - Add deterministic stream input validation

1. Add a small Market Data-local validator or equivalent focused validation
   boundary for WebSocket connection parameters.
2. Require the parameters applicable to each stream type.
3. Validate UUID values before publisher lookup.
4. Accept only the existing supported OHLC intervals unless repository evidence
   requires expanding the contract.
5. Bound order-book depth to the supported values `10` and `25`, or an explicit
   documented superset if existing server capabilities require it.
6. Bound symbol length and reject blank, malformed, or excessively large values.
7. Reject unsupported stream types, malformed integers, duplicate conflicting
   parameters, and missing required parameters deterministically.
8. Ensure rejected setup does not register an upstream subscription.

### Phase 4 - Preserve lifecycle behavior

1. Keep the existing session-ID subscription registry.
2. Preserve disposal on normal connection close.
3. Cover abnormal/failed setup and repeated cleanup without leaking a
   subscription.
4. Avoid introducing global state, retries, or distributed subscription
   coordination.

### Phase 5 - Validate the affected surfaces

1. Extend `MarketDataWebSocketHandlerTest` with parameter and lifecycle cases.
2. Add WebSocket configuration/integration tests for origin behavior.
3. Preserve existing Market Data security tests for internal service callers and
   public HTTP reads.
4. Run the complete Market Data Maven suite.
5. Run the repository quality pipeline and `git diff --check`.
6. Perform one browser-origin WebSocket check through the Gateway using a
   permitted origin and one rejected-origin check.

## Files Expected To Change

- `market-data/src/main/java/.../config/MarketDataWebSocketConfiguration.java`
- `market-data/src/main/java/.../service/MarketDataWebSocketHandler.java`
- Market Data runtime/test configuration files
- `market-data/src/test/java/.../service/MarketDataWebSocketHandlerTest.java`
- New focused WebSocket security/configuration test if existing test seams do
  not cover registration behavior

Frontend files should remain unchanged unless contract verification proves a
compatibility correction is required.

## Constraints

- Preserve ADR-044's public bounded Market Data read model.
- Do not modify service JWT validation or internal endpoint authorization.
- Do not change Gateway routing unless a failing contract test proves it is
  necessary.
- Do not introduce new external dependencies.
- Do not add general rate limiting, quotas, or distributed connection control.
- Do not change broker, execution, risk, or position behavior.
- Do not use a wildcard origin as a fallback in production-like configuration.

## Validation Evidence Required

- Allowed origin establishes a stream.
- Disallowed origin is rejected before subscription creation.
- All current Angular intervals and depths remain accepted.
- Invalid UUID, interval, depth, type, symbol, and missing-parameter cases are
  rejected.
- Existing event forwarding and disconnect cleanup remain green.
- Market Data full Maven tests pass.
- Repository quality pipeline passes.
- No secret, token, or unrelated behavior is exposed by the change.

## Approval Boundary

Implementation proceeded after explicit human approval of this plan.
