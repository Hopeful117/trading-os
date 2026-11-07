# Story 0214 - Agent Identity and Credential Foundation

## Metadata

**ID:** `0214`
**Title:** Agent Identity and Credential Foundation
**Status:** Approved
**Priority:** P1 - Security and Agent Platform
**Related ADRs:** `ADR-024`, `ADR-044`, `ADR-088`, `ADR-089`, `ADR-090`
**Predecessor:** `ADR-090` human architecture approval completed

---

## Goal

Establish the first durable Trading OS foundation for user-owned technical
agents and their credentials without reusing human JWTs or granting any agent
implicit access to user resources.

The outcome is an owner-scoped identity and credential lifecycle that later
Stories can use for agent token exchange, grants, MCP authentication and
controlled account operations.

---

## Context

Trading OS currently authenticates the MCP adapter with a short-lived human JWT
from `TRADING_OS_JWT`. ADR-090 establishes a distinct multi-user agent identity
model. This Story implements only the persistence and owner-controlled
lifecycle foundation for `AgentIdentity` and `AgentCredential`.

Trading Core remains authoritative for user ownership and agent lifecycle.
Raw credentials remain secrets and must not become ordinary domain data.

---

## Problem

There is currently no persisted technical identity for OpenCode or future
agents, no independent credential lifecycle, and no way to revoke one agent
without revoking or reusing a human user's session.

Without this foundation, later delegated authorization would have no durable
agent subject or auditable credential reference.

---

## Scope

- Add the Trading Core domain/application foundation for `AgentIdentity`.
- Add the Trading Core credential metadata foundation for `AgentCredential`.
- Enforce immutable owner assignment from the authenticated user context.
- Support owner-controlled agent registration, status changes, credential issue,
  rotation and revocation.
- Persist only one-way credential verification material and safe metadata.
- Ensure raw credentials are never logged, returned after issuance or persisted.
- Add owner-scoped application contracts for lifecycle operations.
- Add deterministic audit records or lifecycle audit integration for credential
  issue, rotation, revocation and agent status changes, using the repository's
  existing audit boundary where one is suitable.
- Add multi-user authorization and credential-lifecycle tests.
- Preserve the existing user JWT, service JWT and MCP behavior until later
  Stories migrate the adapter.

---

## Out of Scope

- Agent JWT token exchange and agent Spring Security principals.
- `AgentGrant`, permission evaluation or resource-scoped authorization.
- MCP changes or OpenCode configuration changes.
- Account synchronization delegation.
- Human approval tokens.
- Order execution, position mutation, risk overrides or broker operations.
- Broker credentials or cTrader credentials.
- New authorization server, OAuth deployment or remote MCP transport.
- Angular management UI.
- Production deployment, database migration execution or live validation.
- Commit, push or merge.

---

## Acceptance Criteria

- [ ] An authenticated user can register an agent only for their own user ID.
- [ ] The request cannot choose, override or impersonate the agent owner.
- [ ] An agent has an immutable ID, owner, type, display name and lifecycle
      status.
- [ ] Newly registered agents have no implicit permissions or resource access.
- [ ] Credential issuance returns raw credential material only through the
      one-time issuance response.
- [ ] Raw credentials and access tokens are never persisted, logged or returned
      by later read operations.
- [ ] Credential verification uses a one-way hash or equivalent protected
      verification material and supports safe lookup/fingerprint metadata.
- [ ] Credential rotation creates a new credential version and revokes the old
      credential according to the approved lifecycle policy.
- [ ] Revoked, expired or suspended credentials cannot be used by the future
      token-exchange boundary.
- [ ] Agent suspension/revocation invalidates the agent's active credentials
      for future authentication.
- [ ] User A cannot read, rotate, revoke or modify User B's agents or
      credentials.
- [ ] Lifecycle operations produce sanitized, auditable events with correlation
      identity and no secret material.
- [ ] Existing user and service authentication behavior remains unchanged.
- [ ] Targeted Trading Core tests and `git diff --check` pass.

---

## Constraints

- Preserve Trading Core ownership authority and ADR-044 actor rules.
- Keep credentials outside ordinary user/account domain payloads.
- Never reuse human JWTs as agent credentials.
- Deny by default and fail closed on missing or ambiguous ownership.
- Do not trust client-supplied owner, actor or agent identity headers.
- Do not add execution authority or broker access.
- Preserve unrelated uncommitted worktree changes.
- Do not commit, push or merge automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-024.md`
- `docs/architecture/adr/ADR-044.md`
- `docs/architecture/adr/ADR-088-mcp-first-agent-interaction-boundary.md`
- `docs/architecture/adr/ADR-089-native-trading-research-agent-mcp-responsibility-boundary.md`
- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md`

---

## Relevant Modules

- `trading-core`

Broker Service, Gateway, MCP adapter and Angular are not implementation targets
for this first foundation Story unless a narrowly required contract or test
fixture is identified during Repository Analysis and approved in the plan.

---

## Validation

- Targeted Trading Core unit and integration tests for owner isolation,
  lifecycle transitions and credential secrecy.
- Persistence tests for unique ownership and credential-version invariants.
- Security tests for forged owner IDs, cross-user access, revoked credentials,
  expired credentials and suspended agents.
- Repository-standard Java quality validation applicable to Trading Core.
- `git diff --check`.
- No cTrader, broker, order or synchronization calls.

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
