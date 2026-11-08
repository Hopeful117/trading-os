# Repository Analysis - Story 0219

## Status

Completed - human approval required before Implementation Planning.

No production code, migration, runtime database, broker, cTrader, paid AI,
commit, push or merge operation was performed during this analysis.

## Baseline

- Repository: `Hopeful117/trading-os`
- Branch: `main`
- HEAD: `918ff1af7cf5be1a33854c4c5c81c26bcd0a6f79`
- Worktree: clean and synchronized with `origin/main`
- Story 0218: merged in `12096fbe`; its documentation follow-up is merged in
  `918ff1af`.
- Next available Story number: `0219`.

## Context Inspected

The six Research Intelligence audit reports dated 2026-11-08 were read and
reconciled with current code. Stories 0214-0218, their available repository
analyses and implementation reports were inspected. Relevant accepted ADRs were
inspected: ADR-044, ADR-088, ADR-089 and ADR-090.

The current repository is authoritative for implementation state. No usable
DevLog capability was available in this execution context; this is a context
limitation, not a repository blocker. No relevant Obsidian note was required for
this focused security boundary analysis.

## Current End-to-End Path

```text
MCP adapter
  -> POST /api/v1/agents/token through Gateway
  -> short-lived agent JWT
  -> Gateway AgentAuthenticationFilter
  -> public /api/v1/intelligence/** route
  -> Market Intelligence MiJwtAuthenticationFilter
  -> MarketIntelligenceController
  -> ResearchApplicationService
  -> ResearchExecution / ResearchReport
```

The MCP adapter already uses `TRADING_OS_AGENT_CREDENTIAL`, exchanges it through
`/api/v1/agents/token`, caches the token in process memory, retries only GET
requests after `401`, preserves POST idempotency keys and calls only Gateway
routes. The adapter has no grant or ownership logic and should remain that way.

Gateway validates the dedicated agent JWT separately from human JWTs and routes
`/api/v1/intelligence/**`. It removes client `X-Actor-Id` values, but the current
Market Intelligence service only accepts its own user JWT and service JWT
principals. It does not yet validate or construct an `AgentPrincipal`.

## Confirmed Authorization Gap

`MarketIntelligenceController.actorId(Authentication)` accepts only
`MiUserPrincipal`. Both research start and owned status/report retrieval pass that
user ID to `ResearchApplicationService`:

- `POST /api/v1/intelligence/research`
- `GET /api/v1/intelligence/research/{researchId}`
- `GET /api/v1/intelligence/research/{researchId}/report`

`ResearchApplicationService.findOwned` checks only `ResearchExecution.actorId`.
`ResearchExecution` currently persists actor ID, markets, objective, depth,
status, idempotency fingerprint, report and timestamps, but no initiating agent,
grant or research-scope provenance.

Trading Core already provides:

- `AgentPermission.RESEARCH_RUN`;
- `AgentResourceType.RESEARCH_SCOPE`;
- dedicated agent JWT and `AgentPrincipal`;
- active agent/credential validation;
- `AgentAuthorizationService` with exact resource and grant checks;
- sanitized authorization audit events.

But `AgentGrantCompatibility` is the only catalog entry for research and both
`AgentGrantService` and `AgentAuthorizationService` explicitly reject
`RESEARCH_SCOPE` as `UNSUPPORTED_RESOURCE_SCOPE`. Story 0215 intentionally
documents that no stable user-owned research scope exists.

Therefore, simply accepting an `AgentPrincipal` in Market Intelligence would be
unsafe: there is no authoritative exact resource against which `research:run`
can be evaluated, and using owner ID, agent ID, a fixed UUID or request data
would create an implicit wildcard or unvalidated scope.

## Security Design

### Trust boundaries

1. Trading Core authenticates the opaque agent credential and issues the
   dedicated short-lived agent JWT.
2. Gateway validates agent JWT signature, issuer, audience, type and expiry, and
   remains the external ingress.
3. Market Intelligence must independently validate the received agent identity
   or receive a trusted, authenticated delegated context. A client header cannot
   establish identity.
4. Trading Core remains the authoritative grant/ownership service. Market
   Intelligence must not duplicate grant storage or invent a second agent
   registry.
5. `ResearchApplicationService` remains authoritative for lifecycle, idempotency,
   budgets, persistence and report ownership.
6. MCP maps tools to public Gateway contracts and contains no authorization
   business logic.

### Scope contract required before implementation

R1 needs an exact validated `RESEARCH_SCOPE` resource. The smallest coherent
option appears to be a durable Trading Core-owned resource representing a bounded
research capability scope for one owner/agent relationship, with an immutable ID,
owner relationship, lifecycle and explicit validation. The exact shape, whether
it is agent-bound or owner-managed, and how MCP resolves the ID are architectural
decisions that must be approved in the Implementation Plan.

The following are explicitly rejected:

- using `ownerUserId` as the resource ID;
- using `agentId` as an undocumented scope alias;
- using a fixed public UUID or wildcard;
- trusting a caller-supplied scope UUID without server validation;
- enabling `research:run` merely because an agent is authenticated.

### Read semantics

The smallest compatible permission model is to reuse `research:run` for the
agent's own start/status/report lifecycle, provided the research execution stores
the authorized agent and exact scope. A separate read permission is not justified
by the current catalog, but reads must still revalidate active agent/credential
state, grant state, exact scope and execution ownership. Human reads continue to
use the existing owner path.

This decision is provisional and depends on approval of the concrete scope
resource. It must not be implemented as an implicit grant.

### Revocation policy

- Start: always requires a current active agent, credential and exact grant.
- Status/report: require current agent/credential and grant/scope authorization;
  cross-owner and cross-scope access is denied without revealing existence.
- Running execution after revocation: do not silently cancel or mutate lifecycle
  in R1. Existing bounded execution may finish, but subsequent agent reads fail
  closed; human owner retrieval remains governed by normal ownership. Cancellation
  on revocation is a separate explicit decision.

### Audit metadata

Authorization decisions should reuse Trading Core's existing audit boundary and
carry agent ID, owner ID, permission, exact resource/scope ID, decision, reason
code and correlation ID. Research persistence should retain initiating agent and
authorized scope provenance without credentials, JWTs or unrestricted payloads.

## Affected Components

- `trading-core`: scope validation/authorization integration and audit provenance;
- `gateway`: verify existing route/filter behavior and agent identity propagation;
- `market-intelligence`: agent principal handling, research ownership/provenance
  and consistent start/status/report authorization;
- `mcp-adapter`: likely no contract change until scope resolution is approved;
- tests across Trading Core, Gateway, Market Intelligence and MCP adapter;
- a migration may be required only if the approved scope/provenance contract
  cannot reuse existing persistence.

## Required Negative Cases

Missing, expired or revoked grant; unknown/disabled agent; wrong scope; wrong
owner; cross-owner execution/report; forged identity/delegation header; agent
token on human-only endpoints; service token impersonating an agent; agent token
used for broker operations; conflicting idempotency payload; and revocation during
an already-running execution.

## Conclusion and Workflow Gate

The authentication gap is confirmed, but implementation cannot safely start until
the exact `RESEARCH_SCOPE` resource contract is approved. This is a material
security decision, not an implementation detail.

```text
Story 0219                 CURRENT
Repository Analysis        COMPLETED
Repository Analysis Gate   HUMAN APPROVAL REQUIRED
Implementation Plan         NOT STARTED
Production Implementation   NOT AUTHORIZED
```
