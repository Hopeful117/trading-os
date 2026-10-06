# Repository Analysis - Story 0076

## Status

`COMPLETE - IMPLEMENTATION BASELINE ANALYZED`

## Scope

This analysis covers the Market Data WebSocket origin policy, connection input
validation, subscription lifecycle, and the compatibility surface used by the
Angular frontend. It uses Story 0076, ADR-044, the current Market Data source,
existing tests, and the current repository state.

The worktree is clean apart from the newly created Story 0076 artifact. No
implementation changes have been made.

## Current Implementation

### WebSocket registration

`MarketDataWebSocketConfiguration` registers `/ws/market-data` and currently
uses:

```java
.setAllowedOriginPatterns("*")
```

The origin policy is therefore unrestricted and is not externally configured.

### Connection routing

`MarketDataWebSocketHandler` routes connections from URI query parameters:

| Stream | Required parameters | Current publisher |
|---|---|---|
| `TICKER` | `symbol`, `type` | ticker publisher by symbol |
| `OHLC` | `symbol`, `marketId`, `interval`, `type` | OHLC publisher by market and interval |
| `ORDER_BOOK` | `symbol`, `marketId`, `depth`, `type` | order-book publisher by market and depth |
| `TRADES` | `symbol`, `marketId`, `type` | recent-trades publisher by market |

The handler URL-decodes values and parses UUIDs, integers, and enum values. It
does not currently establish explicit bounds for symbol length, interval, depth,
or duplicate/ambiguous parameters.

### Subscription lifecycle

Subscriptions are stored by WebSocket session ID in a concurrent map. Normal
connection closure disposes and removes the upstream subscription. The existing
tests prove normal cleanup, but invalid connection setup can throw during
`afterConnectionEstablished` and there is no explicit boundary test proving
that failed setup never leaves a subscription behind.

### Security boundary

Market Data already has a local service-JWT boundary for `/internal/**` and
keeps bounded public HTTP reads available. Story 0076 must not replace this
model with user authentication for the public WebSocket.

## Existing Tests

`MarketDataWebSocketHandlerTest` currently covers:

- ticker event routing and JSON forwarding;
- OHLC routing;
- recent-trade routing;
- normal disconnect cleanup;
- unsupported stream type rejection.

`MarketDataSecurityIntegrationTest` currently covers:

- unauthenticated internal snapshot rejection;
- valid Trading Core service access;
- wrong caller rejection;
- Market Intelligence endpoint-specific authorization;
- public catalogue access without service credentials.

There is no current test for WebSocket origin allowlisting. There are also no
explicit tests for numeric bounds, malformed UUID/interval/depth inputs,
oversized symbols, duplicate parameters, or cleanup after rejected setup.

## Frontend Compatibility

The Gateway exposes `/ws/market-data` as the public WebSocket route. The
frontend market-data stream service is the consumer that must be checked for
the actual browser origin and parameter ranges before selecting defaults. No
frontend behavior should be changed unless the current contract proves
incompatible with the server-side policy.

## Relevant Architecture Constraints

ADR-044 establishes that:

- public bounded Market Data reads may remain unauthenticated;
- internal and expensive operations require stronger service trust;
- `/internal/**` naming is not itself a security mechanism;
- client-controlled identity headers are not authorization authorities;
- this Story must not introduce an OAuth server, service mesh, or mTLS;
- security behavior must remain deterministic.

Story 0044 explicitly deferred the unrestricted WebSocket origin policy and
identified WebSocket input/resource bounds as part of the remaining Market Data
hardening work.

## Risks

- A strict origin allowlist can break the local Angular development origin if
  the configured port is not discovered and documented.
- Rejecting invalid parameters after a WebSocket upgrade may still allocate
  connection resources; validation should occur at the earliest compatible
  boundary.
- Changing interval or depth limits without checking existing Angular usage can
  silently break the market screens.
- Public-stream origin control is not a substitute for rate limiting or
  distributed connection quotas; those remain out of scope.

## Implementation Boundary

The smallest coherent implementation should be limited to:

- Market Data WebSocket configuration properties and registration;
- deterministic parameter validation in the WebSocket handler or a small local
  validator;
- focused Market Data tests;
- frontend contract inspection only, with no frontend edit unless required;
- relevant configuration/test-profile updates.

Do not modify Market Data service-JWT behavior, Gateway identity propagation,
Trading Core risk logic, or broker execution semantics.

## Recommended Validation

1. Add origin allowlist tests for permitted and rejected origins.
2. Add parameter-boundary tests for every stream type.
3. Add tests proving failed setup creates no persistent subscription.
4. Preserve and rerun existing event-routing and disconnect tests.
5. Run the complete `market-data` Maven suite.
6. Run the repository quality pipeline and `git diff --check`.
7. Perform one local browser-origin WebSocket validation through the Gateway.

## Analysis Conclusion

Story 0076 is implementable without a new ADR or product decision. The current
repository provides a clear implementation boundary and existing test seams.
The only compatibility input required before implementation is the actual
Angular/browser origin and the currently used interval/depth values. These can
be verified from the frontend service and runtime without changing scope.

`STORY0076_READY_FOR_IMPLEMENTATION_PLAN = YES`
