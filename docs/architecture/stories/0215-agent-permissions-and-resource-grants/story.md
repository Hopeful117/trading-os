# Story 0215 - Agent Permissions and Resource Grants

## Metadata

**ID:** `0215`
**Title:** Agent Permissions and Resource Grants
**Status:** Approved
**Priority:** P1 - Security and Agent Platform
**Predecessor:** Story `0214` - Agent Identity and Credential Foundation
**Related ADRs:** `ADR-044`, `ADR-088`, `ADR-089`, `ADR-090`

---

## Goal

Establish the first durable, resource-scoped authorization foundation for
user-owned technical agents. A human owner must be able to grant an individual
agent a specific stable permission over an explicitly identified resource, and
Trading Core must be able to evaluate that grant deterministically.

This Story supports multiple users, multiple agents per user and independent
grant lifecycle management without implementing agent authentication or making
OpenCode privileged.

## Context

Story 0214 added the owner-scoped `AgentIdentity`, credential metadata and
sanitized agent audit foundation. ADR-090 requires explicit grants and default
deny: an active agent identity and credential do not imply access to any user
resource.

Trading Core remains authoritative for agent ownership, broker-account
ownership, grant evaluation and human management operations.

## Scope

- Add a stable typed permission catalog:
  - `accounts:read`
  - `accounts:sync`
  - `positions:read`
  - `markets:read`
  - `risk:read`
  - `research:run`
  - `plans:create`
- Add explicit resource types and resource-scope validation.
- Add persisted `AgentGrant` state and lifecycle.
- Add owner-scoped grant application services and repositories.
- Add a trusted internal `AgentAuthorizationService` that fails closed.
- Add human-authenticated grant management endpoints.
- Integrate grant changes and authorization denials with the existing agent audit
  boundary without logging secrets or unrestricted payloads.
- Add migration, persistence, concurrency and security tests.
- Create the next migration without applying it to any shared runtime database.

## Resource Scope

The initial implementation uses only explicit resource identifiers:

- `BROKER_ACCOUNT`: exact internal `broker_account.id`; supports account,
  position, risk and plan permissions where the operation is account-scoped.
- `MARKET`: exact canonical market ID resolved through the existing market-data
  contract; no missing-ID or wildcard market scope is accepted.
- `RESEARCH_SCOPE`: exact research scope identifier when the existing research
  contract provides a stable scope identity; otherwise the permission remains
  catalogued but grant creation denies unsupported scope requests.

No generic wildcard, owner-wide, provider-account-number or missing-resource
scope is valid.

## Explicit Non-Goals

- Agent authentication or Spring Security agent principals.
- OAuth client credentials, JWT token exchange or authorization server work.
- MCP authorization or OpenCode configuration.
- Broker Service agent delegation.
- Agent self-provisioning, self-granting or grant administration by agents.
- Broker credential or agent credential management through grants.
- `orders:execute`, `orders:modify`, `orders:cancel` or position mutation.
- Angular management UI.
- Agent-driven synchronization or trade execution.
- Shared-runtime migration, deployment, restart, cTrader synchronization or
  broker orders.

## Security Requirements

- The owner is always derived from the authenticated human principal.
- The request cannot select or override the grant owner.
- The target agent must belong to the authenticated user.
- User-owned resources must belong to the authenticated user.
- A grant for one agent never authorizes another agent.
- A grant for one broker account never authorizes another account.
- Missing, unknown, disabled, suspended, revoked, expired or revoked grants
  deny access.
- Revocation is immediate for subsequent authorization decisions and retained
  for audit.
- Client-supplied owner, actor, agent or resource identifiers are targets only,
  never proof of authority.
- No public endpoint evaluates arbitrary agent IDs as authenticated principals.
- Existing user JWT and service JWT behavior remains unchanged.

## Grant Lifecycle Invariants

- Creation requires a supported permission, compatible resource type, explicit
  scope, owned agent, owned target resource where applicable and valid expiry.
- Only one active effective grant may exist for the same agent, permission,
  resource type and resource ID.
- Expiration is evaluated against an injected clock and never extends silently.
- Revocation is idempotent for an owned grant and cannot reactivate it.
- Disabled, suspended, revoked or archived agents have no effective grants.
- Concurrent creation cannot produce duplicate active authorizations.
- Security audit persistence is part of the grant mutation transaction.

## API

Add human-user management endpoints under `/api/v1/agents/{agentId}/grants`:

- `POST /api/v1/agents/{agentId}/grants`
- `GET /api/v1/agents/{agentId}/grants`
- `GET /api/v1/agents/{agentId}/grants/{grantId}`
- `POST /api/v1/agents/{agentId}/grants/{grantId}/revoke`

Endpoints require an authenticated human `UserDto` principal, use
`ResponseEntity`, apply owner-scoped lookups and return sanitized not-found
responses for foreign resources.

## Acceptance Criteria

- [ ] All seven V1 permission identifiers are stable, typed and persisted as
      stable string values.
- [ ] Execution, order mutation, broker credential and agent credential
      permissions cannot be created.
- [ ] A human owner can create a grant only for their own agent.
- [ ] A grant targeting another user's broker account is rejected without
      revealing the resource's existence.
- [ ] Account grants target the exact internal `BrokerAccount.id`.
- [ ] Missing, wildcard, external-account-number and invalid resource scopes are
      rejected.
- [ ] No active agent receives implicit grants.
- [ ] Grant listing and inspection expose only grants for an owned agent.
- [ ] User A cannot read, revoke or alter User B's agent or grants.
- [ ] Agent A cannot use Agent B's grant in authorization evaluation.
- [ ] Authorization fails closed for unknown, inactive, revoked or expired
      agents and grants.
- [ ] Revocation immediately denies subsequent authorization decisions and is
      idempotent.
- [ ] Duplicate active grants are prevented by application validation and a
      database-enforced uniqueness strategy.
- [ ] Concurrent duplicate grant creation cannot result in two active grants.
- [ ] Grant changes and authorization denials produce sanitized audit events.
- [ ] No credential, JWT, private key or broker secret is persisted or logged.
- [ ] Anonymous requests remain denied and existing JWT security behavior is
      unchanged.
- [ ] Story 0214 tests and relevant broker synchronization security tests pass.
- [ ] Migration tests pass without applying a migration to the shared runtime.
- [ ] `git diff --check` passes.

## Test Plan

- Permission/resource compatibility and grant aggregate unit tests.
- Expiration, revocation and agent lifecycle authorization tests with a fixed
  clock.
- Owner, cross-user, cross-agent and wrong-resource application tests.
- Repository and migration tests for foreign keys, indexes and uniqueness.
- Concurrent duplicate creation test using an isolated database.
- Human-authenticated controller tests for success, anonymous denial and
  foreign-resource isolation.
- Story 0214 regression tests and relevant Trading Core security tests.

## Validation Constraints

- Implementation is limited to Trading Core unless an existing contract requires
  a narrowly scoped adapter change.
- No shared runtime database write, deployment, restart, cTrader call, broker
  order or credential provisioning is permitted.
- The implementation must stop after tests, documentation and report; Git
  commit, push and merge remain human-controlled.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Story implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed and reported.
- [ ] Diff reviewed by the human engineer.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
