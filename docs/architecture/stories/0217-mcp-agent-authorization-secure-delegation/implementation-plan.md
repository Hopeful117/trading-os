# Implementation Plan - Story 0217

## Status

Repository Analysis and this Implementation Plan are approved for the current
implementation slice. Route-level agent enablement is limited to the dedicated
Trading Core account boundary; unsupported downstream contracts remain
default-deny.

## 1. Delivery Constraints

- Work only on `story/0217-mcp-agent-authorization-secure-delegation`, based on
  merged `origin/main` commit `5f278e2a`.
- Do not commit, push, merge, deploy, restart services, provision credentials,
  modify a shared runtime database, call cTrader or execute broker operations.
- Never read, extract, print or persist a human JWT or a real agent credential.
- Use mocked Gateway/Broker integrations and isolated databases only.
- Preserve ADR-044, ADR-088, ADR-089 and ADR-090 boundaries.

## 2. Implementation Sequence

### Phase A - Story 0216 security review and regression baseline

1. Add focused tests around Gateway and Trading Core agent filters proving:
   - malformed agent material cannot authenticate;
   - human JWTs remain human principals and cannot enter the agent path;
   - agent JWTs cannot satisfy human or service filters;
   - invalid agent material cannot become an authenticated principal through
     filter ordering or fallback;
   - owner, agent, credential ID and credential version are checked against
     persistence;
   - revoked agent/credential state invalidates an already-issued token in
     Trading Core;
   - issuer, audience, type, subject and expiration remain strict.
2. Change Story 0216 implementation only where a failing security invariant is
   demonstrated. Do not broaden route access as a test workaround.
3. Verify authentication-denial audit behavior. If the current transaction
   rolls denial audit data back, introduce the smallest isolated transaction
   boundary that preserves sanitized denial evidence without exposing an
   enumeration signal.

### Phase B - Agent-aware authorization boundary in Trading Core

1. Add a small request authorization adapter around the existing
   `AgentAuthorizationService`; it must receive the validated `AgentPrincipal`,
   derive the owner from persisted agent identity, and authorize an exact
   permission/resource tuple.
2. Add explicit route/controller handling only for concrete approved read
   operations. Human controllers remain unchanged and continue to require
   `UserDto`.
3. For account collections, query owner-scoped broker accounts and filter each
   item through an exact effective `accounts:read` grant before mapping the
   response. Do not load another owner's resources and filter only in the DTO.
4. Enable account detail/read-only status only with exact
   `BROKER_ACCOUNT` grant checks. Return sanitized not-found/forbidden results
   without revealing resource existence to unauthorized agents.
5. Enable positions, market and risk tools only after identifying existing
   concrete contracts and their exact resource identifiers. If no safe contract
   exists, leave the MCP tool unavailable and document it rather than creating a
   broad proxy.
6. Keep `AgentAccessPolicyFilter` default-deny and replace its blanket behavior
   only with explicit allow-listed route metadata or an equivalent narrow
   authorization boundary.
7. Explicitly deny agent principals for agent lifecycle, grant administration,
   broker credential, plan, execution, position-management and internal routes.

### Phase C - Secure service delegation

1. Extend the existing Trading Core service-JWT delegation model with a
   server-derived agent context. The service JWT must continue to identify
   Trading Core and the Broker Service audience.
2. Derive delegated agent and owner identity only from the validated
   `AgentPrincipal`/persisted owner in `SecurityContextHolder`.
3. Ignore or reject any inbound `X-Delegated-Actor`, owner, agent or equivalent
   client header at the public boundary. Feign headers must never be the source
   of authority.
4. Add only the minimum Broker Service principal fields and validation needed to
   distinguish a trusted delegated agent from a human actor while retaining
   `ROLE_SERVICE` solely for the calling service.
5. Preserve Broker Service service-JWT issuer, audience, type, trusted caller,
   account ownership and internal-route checks. Agent JWTs must be rejected by
   Broker Service security filters.
6. Add forged-header, agent-as-service and service-delegation mismatch tests in
   Trading Core and Broker Service.

### Phase D - MCP credential exchange and tool contract

1. Replace the adapter's `TRADING_OS_JWT` requirement with an agent-specific
   credential variable, for example `TRADING_OS_AGENT_CREDENTIAL`.
2. Add a bounded in-memory token provider that:
   - exchanges the opaque credential through `POST /api/v1/agents/token`;
   - never logs or returns the credential or token;
   - caches the token only in process memory until a safety margin before
     expiration;
   - reauthenticates once after a 401/expired-token response;
   - refuses repeated authentication loops;
   - preserves response and HTTPS limits.
3. Extend the existing MCP client with authenticated GET/POST calls that use the
   token provider. Keep all calls Gateway-only and public-contract-only.
4. Expose only explicitly authorized read-only tools and the synchronization
   request if Phase E proves its human-approval policy can be preserved.
5. Do not expose execution, credential, grant, lifecycle or internal-service
   operations. Preserve generic sanitized tool errors.
6. Add MCP schema, stdio and mocked Gateway tests for exchange, cache reuse,
   expiry, one-shot reauthentication, invalid credentials, authorization denial
   and exact tool inventory.

### Phase E - Controlled synchronization capability

1. Identify or add the smallest Trading Core public command boundary that maps
   to the existing `BrokerSynchronizationServiceImpl.synchronize(ownerId,
   brokerAccountId)` without bypassing human approval.
2. Require an exact active, unexpired `accounts:sync` grant and exact owned
   broker-account UUID before invoking the command.
3. Preserve the existing LIVE-account, provider-registration, external-account,
   snapshot identity, freshness/completeness, cooldown, lease, request-budget
   and observation-deadline checks.
4. If a required human approval cannot be represented in the current command
   path, do not expose the MCP operation. Report it as a deliberate blocker.
5. Use only mocked Broker Service responses in tests. Never use internal target
   `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` or external reference `49411967` for a
   runtime request.

### Phase F - Audit and documentation reconciliation

1. Reuse the existing authorization audit infrastructure where possible.
2. Ensure agent-originated operation records contain agent ID, owner ID,
   permission, resource ID, decision, result, correlation ID and timestamp.
3. Store no JWT, credential, broker payload or provider secret.
4. Update repository `AGENTS.md` with the approved MCP agent trust boundary,
   default-deny authorization, service delegation and no-execution invariants.
5. Update repository `TOOLS.md` with agent credential configuration, token
   exchange usage, MCP test commands, no-human-JWT guidance and explicit
   prohibition on live synchronization.
6. Update `mcp-adapter/README.md` with the agent credential trust boundary and
   tool inventory.
7. Update the global OpenCode config
   `/home/ludo/.config/opencode/opencode.jsonc` only to reference the
   agent-credential environment variable for the local `trading-os` MCP server;
   never place a real credential in the file. Remove the `TRADING_OS_JWT`
   dependency from that MCP entry. This external configuration change requires
   an OpenCode restart and must not be committed to the repository.
8. Record the configuration change and restart prerequisite in the
   Implementation Report.

## 3. Files Expected To Change

### Repository documentation

- `docs/architecture/stories/0217-mcp-agent-authorization-secure-delegation/story.md`
- `docs/architecture/stories/0217-mcp-agent-authorization-secure-delegation/repository-analysis.md`
- this `implementation-plan.md`
- `AGENTS.md`
- `TOOLS.md`
- `mcp-adapter/README.md`

### MCP adapter

- `mcp-adapter/app/client.py`
- `mcp-adapter/app/server.py`
- new token-provider module if needed for separation and testability;
- `mcp-adapter/tests/test_client.py`
- `mcp-adapter/tests/test_server.py`
- focused token-provider tests.

### Gateway

- existing agent filter/security configuration only if Phase A proves a defect;
- focused security and route regression tests.

### Trading Core

- agent authorization adapter/controller boundary;
- service-JWT delegation interceptor/principal changes;
- explicit account/approved read/synchronization endpoint integration;
- focused authorization, ownership, audit and delegation tests.

### Broker Service

- service principal/delegation validation only if required by the approved
  delegation design;
- service security and forged-delegation regression tests.

### External local configuration

- `/home/ludo/.config/opencode/opencode.jsonc`, modified only to reference
  `TRADING_OS_AGENT_CREDENTIAL` for the existing local MCP process. No real
  secret is written and no repository commit includes this file.

No migration is planned initially. A migration may be added only if the audit
contract cannot be satisfied with existing tables and after documenting the
minimal schema requirement and isolated migration tests.

## 4. Validation Plan

Run only after implementation:

```text
cd mcp-adapter && .venv/bin/pytest
cd mcp-adapter && .venv/bin/python -m compileall -q app tests
cd gateway && ./mvnw -q test
cd trading-core && ./mvnw -q test
cd broker-service && ./mvnw -q test
git diff --check
```

Run targeted tests first for agent filters, authorization, delegation,
synchronization guards and MCP token lifecycle. Run packaging/quality commands
applicable to changed modules after targeted tests pass. No live external
network or provider validation is part of this Story.

## 5. Risks and Stop Conditions

Stop and request human guidance if:

- an accepted ADR must change;
- human approval semantics for synchronization are not enforceable;
- an existing public contract cannot express exact agent resource scope;
- service delegation would require trusting a client-controlled header;
- an operation would require execution, broker credential access or a live
  provider call;
- a shared database migration or runtime configuration write becomes necessary;
- the OpenCode configuration schema does not validate the planned environment
  reference.

## 6. Workflow State

```text
Story 0217                 DRAFT
Repository Analysis        APPROVED BY HUMAN
Implementation Plan        APPROVED
Credential/delegation slice IMPLEMENTED
Route-level agent access    ACCOUNT READS AND POSITIONS IMPLEMENTED
Production Implementation   NOT AUTHORIZED
```
