# Story 0216 - Agent Authentication and Token Exchange

## Metadata

**ID:** `0216`
**Title:** Agent Authentication and Token Exchange
**Status:** Draft
**Priority:** P1 - Security and Agent Platform
**Predecessors:** Stories `0214` and `0215`
**Related ADRs:** `ADR-044`, `ADR-088`, `ADR-089`, `ADR-090`

## Goal

Provide secure machine authentication for user-owned technical agents. An
external agent client, initially OpenCode, must authenticate with its dedicated
opaque credential and receive a short-lived, cryptographically signed agent JWT
that is distinguishable from human and service authentication.

Authentication must prove agent identity only. Resource access remains governed
by the existing explicit `AgentGrant` model and default-deny authorization.

## Scope

- Add a dedicated agent credential exchange endpoint.
- Validate credential, credential status, agent status and persisted ownership.
- Issue a short-lived agent JWT with explicit issuer, audience, subject, actor
  type, owner, issuance, expiration and token identifier claims.
- Add a distinct agent principal representation.
- Validate agent tokens in Gateway and Trading Core without changing human or
  service authentication semantics.
- Keep agent management endpoints human-only.
- Ensure revoked agents and credentials cannot authenticate, and protected
  Trading Core operations perform live lifecycle validation for already-issued
  agent tokens.
- Add safe authentication audit events and generic external failures.
- Add focused security, persistence and regression tests.
- Update relevant OpenAPI and repository documentation.

## Explicit Non-Goals

- OAuth authorization server or remote MCP OAuth.
- MCP adapter changes or MCP tool authorization.
- Broker delegation, account synchronization or execution.
- Agent self-provisioning, self-granting or grant administration.
- Human JWT replacement or service JWT replacement.
- Real credential provisioning, deployment, shared-runtime migration or
  runtime operations.

## Security Requirements

- Never reuse human JWTs, service JWTs or their signing keys for agent tokens.
- Never trust client-supplied owner, actor, agent, role, audience or lifetime
  claims.
- Resolve owner and agent state from trusted persistence.
- Do not log, persist or return raw credentials or JWTs.
- Return generic external authentication failures without credential
  enumeration.
- Agent authentication must not imply any `AgentGrant` permission.
- Agent principals must not satisfy human-only or service-only endpoints.
- Gateway must not trust client-controlled identity headers.
- Existing human and service authentication must remain compatible.

## Revocation Policy

Credential or agent revocation prevents new exchanges immediately. Trading Core
must perform a live active-state validation for an already-issued agent token
before agent-protected operations. The token remains bounded by its short
expiration; Gateway cryptographic validation alone is not treated as immediate
revocation.

## Acceptance Criteria

- [ ] Valid active credentials exchange for short-lived agent JWTs.
- [ ] Missing, malformed, unknown, revoked or expired credentials are rejected
      with generic failures.
- [ ] Suspended or revoked agents cannot exchange credentials.
- [ ] Agent JWTs use a distinct token type, issuer, audience and signing key
      from human and service tokens.
- [ ] Agent JWT subject identifies the agent, while owner identity is resolved
      from persisted ownership and never selected by the client.
- [ ] Required claims are validated strictly, including signature, issuer,
      audience, token type and expiration.
- [ ] No arbitrary roles, permissions, audiences or lifetimes are accepted.
- [ ] Human tokens cannot enter the agent-token path and agent tokens cannot
      satisfy human or service principals.
- [ ] Human agent management endpoints reject agent principals.
- [ ] Revocation after token issuance denies subsequent protected agent
      operations through Trading Core live validation.
- [ ] Existing human and service authentication regression tests pass.
- [ ] Authentication audit records contain safe identifiers and no secrets.
- [ ] No credentials are provisioned and no runtime operation is performed.
- [ ] Focused tests, applicable Maven validation and `git diff --check` pass.

## Validation

- Targeted Trading Core and Gateway unit/security tests.
- Credential lifecycle and token exchange integration tests with isolated data.
- Existing human JWT, service JWT and Gateway regression tests.
- Maven compilation, tests and applicable packaging/quality validation.
- `git diff --check`.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Validation executed and reported.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
