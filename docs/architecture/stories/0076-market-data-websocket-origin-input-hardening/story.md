# Story 0076 - Market Data WebSocket Origin and Input Hardening

## Metadata

**ID:** `0076`
**Title:** Market Data WebSocket Origin and Input Hardening
**Status:** CLOSED - HUMAN ACCEPTED

The WebSocket hardening implementation and runtime security evidence were
reviewed and accepted by the human engineer. The final human Git commit remains
pending under the repository workflow; this status does not authorize another
implementation, commit, push, or merge operation.
**Related Story:** `0044-api-surface-hardening-ownership-enforcement`
**Related ADR:** `ADR-044 - Service-to-Service Trust and Actor Propagation`

---

## Goal

Make the public Market Data WebSocket a deliberately bounded browser-facing
surface by replacing unrestricted origin acceptance with explicit configuration
and by rejecting invalid or unsafe stream parameters.

---

## Context

Story 0044 established the V1 trust model and protected Market Data internal
HTTP operations while preserving public bounded market reads. Its remaining
WebSocket gap is the unrestricted origin policy in
`MarketDataWebSocketConfiguration`.

The WebSocket carries public market data and therefore does not require user
JWT authentication solely for opening a stream. It must nevertheless enforce a
bounded browser origin policy and safe resource/input limits.

---

## Problem

The Market Data WebSocket currently accepts every origin through
`setAllowedOriginPatterns("*")`. Stream parameters are parsed directly from the
request URI, with no clearly enforced bounds for values such as order-book
depth, and invalid connection parameters are not covered by a complete
boundary test suite.

This leaves the public stream dependent on an unrestricted browser-origin
policy and exposes avoidable resource-abuse and malformed-input risks.

---

## Scope

- Replace the unrestricted WebSocket origin pattern with a configurable
  allowlist.
- Preserve explicit local/test configuration while making production-like
  configuration fail safely when no permitted origin policy is defined.
- Verify compatibility with the Angular browser origin used by the application.
- Validate required stream parameters before creating subscriptions.
- Enforce bounded values for stream type, market identifiers, intervals, depth,
  and symbol input according to existing frontend usage and domain limits.
- Close or reject invalid WebSocket connection requests without creating a
  subscription.
- Preserve subscription cleanup on normal and abnormal connection closure.
- Add focused unit/integration tests for allowed origins, rejected origins,
  malformed parameters, bounds, and cleanup.

---

## Out of Scope

- User authentication for public market-data streams.
- Changes to Market Data domain ownership or market-data normalization.
- Changes to internal service JWT authentication.
- New market-data providers or broker integrations.
- Rate limiting, distributed connection quotas, or a general abuse-prevention
  platform.
- Changes to Gateway routing beyond what is strictly required by a failing
  contract test.
- Kraken Sandbox execution or broker reconciliation.

---

## Acceptance Criteria

- [ ] The WebSocket no longer uses an unrestricted `*` origin policy.
- [ ] Allowed origins are externally configurable and test/local defaults are
      explicit rather than implicit.
- [ ] A configured Angular application origin can establish the expected public
      stream.
- [ ] A disallowed origin is rejected before a market subscription is created.
- [ ] Missing, malformed, unsupported, and out-of-range stream parameters are
      rejected deterministically.
- [ ] Order-book depth and other resource-sensitive inputs have explicit bounds.
- [ ] Invalid connection attempts do not leak subscriptions or resources.
- [ ] Normal and abnormal connection closure disposes the associated stream.
- [ ] Existing public market-data HTTP reads remain functional.
- [ ] Existing internal Market Data service-authentication behavior remains
      unchanged.
- [ ] Focused Market Data tests pass.
- [ ] No unrelated behavior is changed.

---

## Constraints

- Preserve public bounded market-data access as defined by ADR-044.
- Do not use client-controlled identity headers as authorization authority.
- Keep security and validation behavior deterministic.
- Do not weaken internal service authentication.
- Do not introduce a new authentication server, service mesh, or external
  dependency.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-044.md`

---

## Relevant Modules

- `market-data`
- `trading-os-web` only for origin and stream-contract verification

---

## Validation

- Run focused Market Data WebSocket and security tests.
- Run the complete `market-data` Maven test suite.
- Run the repository quality pipeline when implementation is complete.
- Verify a permitted browser-origin WebSocket connection manually in the local
  environment.
- Verify a rejected-origin and malformed-parameter connection manually or with
  an integration test.
- Run `git diff --check`.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
