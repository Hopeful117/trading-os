# Story 0217 - MCP Agent Authorization and Secure Delegation

## Metadata

**ID:** `0217`
**Title:** MCP Agent Authorization and Secure Delegation
**Status:** Implementation complete for read-only account boundary; synchronization remains disabled
**Priority:** P1 - Agent Platform Security
**Predecessors:** Stories `0214`, `0215`, `0216`
**Related ADRs:** `ADR-044`, `ADR-088`, `ADR-089`, `ADR-090`

## Goal

Enable OpenCode to use the existing local stdio MCP adapter as a registered,
user-owned Trading OS agent. The adapter must exchange its opaque agent
credential for a short-lived agent JWT and use only explicitly authorized,
read-only Trading OS operations through the existing Gateway and Trading Core
boundaries.

The first supported business capability is safe account inspection and a
controlled account-synchronization request. No trading execution is exposed.

## Scope

- Start from merged `main` at Story 0216 merge commit `5f278e2a`.
- Harden Story 0216 authentication behavior where the security audit identifies
  an exploitable bypass or unsafe failure path.
- Extend the existing MCP adapter; do not add a second MCP server or generic
  HTTP proxy.
- Replace the MCP adapter's human JWT dependency with agent credential exchange,
  in-process short-lived token handling and bounded reauthentication.
- Map actual MCP operations to explicit `AgentGrant` permissions and exact
  resources.
- Enable only approved read-only account, position, market and risk operations
  that have concrete existing Trading OS contracts.
- Add a controlled account synchronization request requiring `accounts:sync`,
  exact broker-account ownership and all existing broker safety controls.
- Preserve Gateway cryptographic validation and Trading Core live lifecycle and
  grant validation.
- Preserve Trading Core to Broker Service service-JWT authentication.
- Add signed, trustworthy agent delegation context for internal calls without
  accepting client-controlled identity headers.
- Keep human-only agent management, broker credential management and execution
  endpoints unavailable to agent principals.
- Add safe operation audit records and focused regression/integration tests.
- Update MCP documentation, configuration documentation and Story artifacts.

## Explicit Non-Goals

- No order placement, modification, cancellation, position management or trade
  execution MCP tool.
- No agent self-provisioning, self-granting or grant administration.
- No broker credential management through MCP.
- No human JWT extraction, reuse or fallback.
- No OAuth authorization server, remote MCP OAuth or remote MCP transport.
- No automatic authorization grants.
- No live credential provisioning, deployment, restart or shared runtime write.
- No cTrader synchronization execution during implementation or testing.
- No changes to accepted ADRs.

## Security Invariants

- The MCP runtime sends an opaque agent credential only to the token exchange
  endpoint; it never logs or persists the credential.
- The MCP runtime stores the issued JWT only in process memory and refreshes it
  after expiration or an authentication response.
- Agent authentication never implies a grant, role, service identity or human
  identity.
- Gateway and Trading Core validate agent JWT signature, issuer, audience, type,
  subject, required claims and expiration using the dedicated agent key.
- Trading Core resolves agent, owner, credential and grant state from trusted
  persistence on every protected operation.
- Every resource access checks exact resource ownership and effective grant
  status/expiration; collections are filtered before response construction.
- Client-supplied owner, actor, agent and delegation headers are never trusted.
- Internal Broker Service calls use service JWTs only. Agent context may be
  carried only inside a server-issued, validated service delegation claim.
- Broker Service never accepts an agent JWT as a service JWT and never grants an
  agent service authority.
- Authentication and authorization denial audits contain identifiers and reason
  codes only, never credentials, JWTs or broker payloads.
- Default deny remains active for every route not explicitly enabled.

## Initial Authorization Matrix

| MCP capability | Trading OS operation | Permission | Resource |
|---|---|---|---|
| List authorized accounts | Read broker-account collection | `accounts:read` | Each returned `BROKER_ACCOUNT` |
| Read account details/status | Existing broker-account read contract | `accounts:read` | Exact `BROKER_ACCOUNT` |
| Read positions | Existing authorized account/position contract | `positions:read` | Exact `BROKER_ACCOUNT` |
| Read market information | Existing public market contract | `markets:read` | Exact canonical `MARKET` |
| Read risk status | Existing account risk/read contract | `risk:read` | Exact `BROKER_ACCOUNT` |
| Request synchronization | New or adapted controlled command | `accounts:sync` | Exact `BROKER_ACCOUNT` |

The account, account-status and position rows are backed by the dedicated
`/api/v1/agent/broker-accounts/**` Trading Core boundary. Synchronization,
risk, market and research rows remain unavailable unless their existing
downstream contracts can enforce the same resource-scoped semantics.

## Synchronization Safety

The implementation must preserve existing provider, ownership, snapshot,
cooldown, lease, request-budget, observation-deadline and completeness checks.
The internal account target used for validation is
`7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`; the external cTrader account reference
`49411967` is test data only. No synchronization request may be sent against a
live or demo provider during this Story.

If the existing human-approval policy cannot be enforced for an agent-originated
synchronization request, synchronization remains disabled and the blocker is
reported rather than bypassed.

## Acceptance Criteria

- [x] The existing MCP stdio server authenticates with an agent credential and
      never requires or sends a human JWT.
- [x] Credential exchange, token caching, expiration and reauthentication are
      bounded, safe and covered by tests.
- [x] Invalid, expired, revoked and human credentials cannot authenticate as an
      agent; agent tokens cannot authenticate as humans or services.
- [x] Story 0216 bypass findings, if any, are fixed with regression tests.
- [x] Each enabled MCP operation performs explicit agent, owner, grant, resource
      ownership, compatibility, expiration and revocation checks.
- [x] Account collections cannot leak another user's resources through rows,
      pagination, counts or error details.
- [x] Agent management, grant administration, broker credentials and execution
      endpoints remain denied.
- [x] Trading Core delegates only a trusted, validated agent context to Broker
      Service through the existing service-JWT boundary.
- [x] Forged delegation headers and agent-as-service attempts are rejected.
- [ ] Synchronization requires an exact `accounts:sync` grant and preserves all
      existing cooldown, lease, snapshot and human-approval safeguards.
- [x] No cTrader synchronization, broker order or live runtime operation occurs.
- [ ] Operation audits contain agent, owner, permission, resource, decision,
      result, correlation ID and timestamp without secrets.
- [x] MCP, Trading Core, Gateway and Broker Service security regressions pass.
- [x] Applicable Maven/Python validation and `git diff --check` pass.

## Validation Expectations

- MCP adapter unit, schema and stdio integration tests with mocked Gateway.
- Trading Core authorization, controller, delegation and persistence tests.
- Gateway agent-authentication and route regression tests.
- Broker Service service-JWT and forged-delegation regression tests.
- Isolated migration tests only if a schema change is strictly necessary.
- `./mvnw -q test` for affected Java modules.
- MCP adapter test suite and Python compilation checks.
- `git diff --check`.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [x] Implementation completed.
- [x] Documentation reconciliation completed.
- [x] Implementation Report completed.
- [x] Code Review completed.
- [x] Engineering Report completed.
- [ ] No commit, push, merge or deployment performed by this Story execution.
