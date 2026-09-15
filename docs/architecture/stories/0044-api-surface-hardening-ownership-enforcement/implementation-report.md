# Implementation Report - Story 0044

## Status

`IMPLEMENTED - RUNTIME VALIDATED; DEFERRED DEBT RECORDED`

## Implemented

- Added service JWT properties, creation, validation, and principal handling in
  the affected services.
- Added receiver-side authorization for Broker Service and Market Intelligence
  internal calls.
- Added Core Feign configurations for receiver-specific service credentials and
  delegated actor propagation.
- Hardened affected Trading Core and Broker Service controller/service paths.
- Removed the predictable production JWT fallback while retaining explicit test
  configuration.
- Added security regression tests and a real-socket acceptance module.
- Added Story 0044 ADR, investigation, and authorization/identity matrices.
- Fixed the MI error-dispatch path with
  `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.

## Runtime Acceptance

The acceptance harness starts Broker Service, Market Intelligence, and Trading
Core in one JVM with real HTTP sockets and real Feign calls. It verifies:

- Core user ingress reaches both downstream receivers;
- receiver audience isolation;
- separation between user and service trust classes;
- missing delegated actor rejection;
- actor mismatch rejection;
- valid service credentials without authorized caller rejection;
- invalid signing-key rejection.

The harness passes with `mvn -q test`.

## Explicitly Deferred Scope

The following are intentionally not absorbed into this closure commit:

- Gateway architecture and Gateway-to-Execution proof;
- Market Data security and bounded WebSocket policy;
- standalone analysis ownership, which remains fail-closed;
- the complete normal-user PAPER journey.

The validated Core/Broker/MI ownership and service-authentication tests passed;
additional endpoint families above retain their explicit Story/follow-up
classification in the matrices.

## Repository State

No commit, push, merge, reset, or unrelated-change cleanup was performed.
