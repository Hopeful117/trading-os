# Repository Analysis - Story 0044

## Status

`COMPLETE - IMPLEMENTATION BASELINE ANALYZED`

## Scope

The analysis uses the approved Story, ADR-044, the API security audit, and the
current worktree. The Story is large and coherent, but the current implementation
does not yet cover every acceptance criterion.

## Confirmed Implementation Areas

- Trading Core and Broker Service retain local user security boundaries.
- Service JWT primitives exist in Trading Core and Broker Service.
- Market Intelligence has a local service-principal boundary.
- Core Feign clients can emit receiver-specific service credentials.
- Delegated actor context is represented in signed service JWT claims for the
  current Core-to-Market-Intelligence flow.
- The Gateway-to-downstream execution and Market Data changes still require
  separate validation before being considered complete.

## Relevant Boundaries

| Boundary | Authority | Current evidence |
|---|---|---|
| User authentication | Local user JWT principal | Trading Core and Broker Service security chains |
| Service authentication | Receiver-validated signed service JWT | Core, Broker, and Market Intelligence security classes |
| Delegated actor | Signed `actor_id` claim | Core-to-Market-Intelligence Feign configuration |
| Resource ownership | Owning service/domain relation | Trading Core and Broker changes, requiring two-user regression proof |
| Shared opportunities | Authenticated shared visibility | Market Intelligence authorization matrix |

## Risks and Gaps

- The Story's full Trade IDOR matrix must be verified with persisted `USER_A` and
  `USER_B` fixtures.
- Gateway header sanitation and real Gateway-to-Execution mapping require
  dedicated runtime evidence.
- Market Data internal endpoint protection and WebSocket origin policy require
  implementation and tests.
- Ambiguous legacy endpoints must remain restricted or explicitly deferred; they
  must not be removed based only on the absence of an Angular consumer.
- Full normal-user PAPER journey acceptance remains out of scope.

## Analysis Conclusion

The current code supports a focused V1 security implementation and a real-socket
Core-to-Broker/Core-to-Market-Intelligence acceptance harness. It does not justify
claiming that every Story 0044 acceptance criterion is complete.
