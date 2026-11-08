# Implementation Plan - Story 0219

## Status

Pending human approval.

## Objective

Enable one secure vertical slice for agent-owned bounded research while keeping
Market Intelligence authoritative for research execution and Trading Core
authoritative for agent identity, scope ownership and grant evaluation.

## Approved Design Direction

Introduce an exact durable `ResearchScope` resource instead of treating an owner,
agent or fixed UUID as a wildcard. A scope is owned by one Trading Core user and
contains an explicit bounded set of canonical market IDs. A human owner creates
and grants the scope to an agent. The agent request must use market IDs contained
in that scope.

The exact scope ID is not accepted as proof by itself. Trading Core validates the
scope owner, lifecycle, expiration and market membership on every authorization
call.

The `research:run` grant is reused for start, status and report reads. No new
permission is introduced. Research execution records retain the initiating agent,
owner, scope and authorization provenance. Human research continues to use the
existing `MiUserPrincipal` and owner-scoped path.

## Trust Flow

```text
MCP adapter
  -> agent credential exchange at Gateway
  -> short-lived agent JWT
  -> Gateway validates agent token and routes public research request
  -> Market Intelligence independently validates the agent JWT
  -> Market Intelligence calls Trading Core internal authorization contract
     with its server-issued service JWT and agent/scope/market targets
  -> Trading Core validates agent lifecycle, exact ResearchScope, market subset,
     active research:run grant and audit decision
  -> Market Intelligence starts or reads the existing ResearchExecution
  -> report remains advisory and owned by the authorized agent/owner relationship
```

The MCP adapter remains unchanged unless a scope identifier must be added to the
existing start contract. Authorization logic remains entirely in Java.

## Implementation Steps

### 1. Trading Core ResearchScope

- Add a small owner-scoped `ResearchScope` model/entity with:
  - immutable scope ID;
  - owner user ID;
  - explicit canonical market IDs;
  - active/revoked lifecycle;
  - created, updated and optional expiry timestamps;
  - version for audit/concurrency.
- Add repository queries that always include owner or exact scope ID.
- Add human-only scope creation/list/revoke application operations under the
  existing agent-management boundary. Scope creation validates every market
  through the existing Market Data client.
- Add migration `V57__agent_research_scopes.sql` with owner foreign key,
  lifecycle constraints, explicit market-set storage and lookup indexes.
- Extend `AgentGrantService` and `AgentAuthorizationService` so
  `RESEARCH_SCOPE` is accepted only when the exact scope exists, is owned by the
  agent owner, is active/unexpired, and contains the requested market IDs.
- Preserve default deny and existing audit failure policy.

### 2. Trading Core Internal Authorization Contract

- Add a narrow `/internal/v1/agent/research/authorize` endpoint.
- Require an authenticated `market-intelligence` service principal and the
  existing service JWT audience/issuer validation.
- Accept only target data: agent ID, scope ID, market IDs, research ID when
  reading, operation type and correlation ID. Do not accept an owner ID as
  authority.
- Return a sanitized authorization decision containing owner ID, scope ID,
  grant ID/version and agent version; never return credentials, JWTs or grant
  payloads.
- Reuse `AgentAuthorizationService`; do not duplicate grant logic in the
  controller or Market Intelligence.
- Add a Feign client/configuration in Market Intelligence that issues the
  existing short-lived service JWT for the `trading-core` audience using the
  configured MI service secret.

### 3. Market Intelligence Agent Principal and Security

- Add a Market Intelligence agent JWT validator using the existing dedicated
  agent-JWT configuration and claims, with strict issuer, audience, type,
  subject, credential version and expiration validation.
- Preserve the existing human JWT and service JWT filters and reject agent
  principals from human-only endpoints.
- Extend the research controller/application boundary to distinguish human and
  agent calls without replacing `MiUserPrincipal`.
- For agent calls, require the internal Trading Core authorization decision before
  starting or reading research.
- Do not trust `X-Actor-Id`, request owner IDs or client delegation headers.

### 4. Research Ownership and Provenance

- Extend `ResearchExecution` with agent provenance:
  - owner user ID;
  - initiating agent ID, nullable for human-created research;
  - exact research scope ID, nullable for human-created research;
  - authorization grant/version metadata where needed for audit.
- Preserve `actorId` compatibility as the effective owner actor for human and
  agent ownership queries.
- Add migration `V21__research_agent_ownership.sql` to the Market Intelligence
  research table.
- Update `ResearchApplicationService.start` and `findOwned` so:
  - humans retain current owner behavior;
  - agents can access only executions created by that agent under the same owner
    and scope;
  - a revoked/expired grant denies later agent reads;
  - an execution already running is not silently cancelled by revocation;
  - human owner retrieval remains available through the human path.
- Keep current idempotency fingerprint semantics and include scope/agent context
  in the agent request fingerprint.

### 5. Gateway and MCP Verification

- Preserve the existing `/api/v1/intelligence/**` route and agent token filter.
- Verify Gateway removes client identity headers and does not authorize research
  based on a header.
- Reuse the MCP tools and HTTP paths wherever possible. Add a scope input only
  if the approved public contract requires the client to select among multiple
  scopes; do not add grant-management logic to Python.
- Preserve POST no-retry behavior and GET bounded reauthentication.
- Document the scope provisioning prerequisite and sanitized authorization
  failures.

## Test Plan

### Trading Core unit/application tests

- scope creation validates canonical markets and owner isolation;
- scope revocation/expiry denies authorization;
- exact scope/market subset authorization succeeds;
- wrong scope, missing grant, expired grant, revoked grant and cross-owner scope
  deny;
- agent permission cannot authorize broker operations;
- authorization audit records contain safe IDs and correlation only.

### Market Intelligence security/controller tests

- human research remains functional;
- valid agent principal plus Trading Core allow decision starts research;
- agent status/report reads require the same agent, owner and scope;
- invalid agent, human/service confusion and forged headers are denied;
- cross-owner and cross-scope research IDs are indistinguishable from not found;
- revocation denies subsequent agent reads while human owner read remains valid;
- idempotent repeat and conflicting payload behavior remains unchanged.

### Integration tests

Use isolated test infrastructure for:

```text
agent credential exchange
  -> Gateway agent authentication
  -> Market Intelligence agent authentication
  -> Trading Core service authorization
  -> persisted research creation
  -> status retrieval
  -> report retrieval
```

The integration path must use test keys and offline research/AI fixtures only.
No paid provider, live broker or shared runtime database is allowed.

### MCP tests

- existing tool schemas and paths remain compatible unless scope input is
  explicitly required;
- agent credential exchange and token caching remain unchanged;
- POST research creation is never automatically replayed;
- sanitized 401/403/404 failures do not disclose tokens, owner data or reports.

## Validation Commands

- `trading-core`: targeted agent/scope/security tests, migration tests, then
  `./mvnw -q test`;
- `market-intelligence`: targeted research/security tests, migration tests, then
  `./mvnw -q test`;
- `gateway`: agent route/filter regression tests, then `./mvnw -q test`;
- `mcp-adapter`: `.venv/bin/pytest` and Python compilation checks;
- `git diff --check`;
- repository quality validation for all affected modules.

## Files Expected To Change

- `trading-core` agent scope model, repository, service, controller, authorization
  service, migration and tests;
- `market-intelligence` security, research controller/application/domain,
  Trading Core client/configuration, migration and tests;
- `gateway` only if a focused agent-route/header regression requires a change;
- `mcp-adapter` only if the approved scope input contract requires a minimal
  schema/documentation adjustment;
- Story 0219 implementation report and documentation reconciliation artifacts.

## Out Of Scope

- changing research algorithms, AI prompts or capability allowlists;
- adding a new MCP tool;
- account synchronization or execution;
- new broker/service authorization models;
- production deployment, shared database migration or credential provisioning;
- commit, push or merge.

## Approval Gate

Implementation must not begin until this plan is explicitly approved.
