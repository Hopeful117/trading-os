# Repository Analysis - Story 0217

## Status

Completed and reconciled with the current read-only implementation slice.

## Baseline

- Repository: `Hopeful117/trading-os`
- Working branch: `story/0217-mcp-agent-authorization-secure-delegation`
- Branch base: `origin/main`
- `origin/main` HEAD: `5f278e2a2a866c8f95376666266bf99353c60ad5`
- Verified ancestor: Story 0216 merge commit `5f278e2a`
- Worktree: clean before Story 0217 artifacts
- Next available Story number: `0217`
- No commit, push, merge, deployment, provisioning or broker/cTrader call was
  performed during this analysis.

## Context Sources

Authoritative repository sources inspected:

- `ADR-044` service-to-service JWT and delegated actor boundary;
- `ADR-088` local MCP boundary;
- `ADR-089` agent/MCP responsibility constraints;
- accepted `ADR-090-agent-identity-permissions-delegated-authorization`;
- Stories `0214`, `0215` and `0216`, including their repository analyses and
  implementation report;
- existing MCP adapter, Gateway, Trading Core and Broker Service code;
- current tests, migrations and security configuration.

DevLog was queried for targeted Story 0217 context. It completed with a
truncated, low-confidence projection that did not contain usable architecture
findings or implementation preparation. Repository evidence and accepted ADRs
remain authoritative. A targeted Obsidian search for MCP agent authorization and
Trading OS agent JWT/grant context returned no relevant notes.

## Existing MCP Boundary

`mcp-adapter/` is the existing local stdio MCP server. It uses the official MCP
Python SDK and exposes:

- `list_markets`;
- `get_active_scan`;
- `get_market_thesis`;
- `get_opportunities`;
- `start_market_research`;
- `get_research_status`;
- `get_research_report`.

The adapter currently reads `TRADING_OS_JWT`, sends it as an HTTP bearer token
to the public Gateway, and keeps no token persistence. This is the main Story
0217 gap: it currently depends on a human JWT and has no agent credential
exchange or authorization-aware operation contract.

The safest trust model is a trusted local MCP runtime boundary: OpenCode starts
the stdio adapter, the adapter receives a configured agent credential from its
process environment, exchanges it with Trading Core through the Gateway, and
keeps the short-lived JWT only in memory. OpenCode itself must not receive or
need a human JWT. The adapter must not accept arbitrary agent or owner IDs.

The existing market/research tools do not all have broker-account resource
identifiers. They must not be automatically treated as agent-authorized until
their concrete backend contract can resolve the required grant scope. Public
market reads may be enabled only after mapping canonical market IDs to
`MARKET` grants without weakening existing public behavior.

## Story 0216 Security Findings

Trading Core `AgentAuthenticationFilter` validates the dedicated agent JWT and
performs live agent and credential status/version checks. Gateway performs
cryptographic agent-token validation. Both use a distinct key, issuer,
audience and `type=agent` claim. Human and service filters remain separate.

The current filters catch invalid agent-token failures and continue the chain so
the existing human filter can accept a valid human token. This is compatible
with dual authentication only if the human filter cannot accept agent material;
0217 must add explicit regression tests for malformed, human, agent and service
tokens and ensure an invalid agent token cannot fall through to an unintended
less restrictive path.

Trading Core currently has `AgentAccessPolicyFilter`, which rejects every agent
principal. This correctly preserves default deny but means no agent business
operation is currently enabled. 0217 must replace that blanket behavior only
with explicit route/controller authorization and retain denial for all other
routes.

The token exchange records authentication audit events inside the exchange
transaction. 0217 must verify that denial audits survive failed exchange
transactions or otherwise implement an explicit independent audit transaction;
this must not weaken generic external failures or create credential enumeration.

## Existing Agent Authorization

Trading Core already contains `AgentAuthorizationService.authorize(...)`, which
is the required grant source of truth. It checks:

- agent existence and active lifecycle state;
- permission/resource compatibility;
- exact broker-account ownership through `findByIdAndOwnerId`;
- canonical market existence through `MarketDataClient`;
- effective grant status and expiration;
- sanitized authorization audit persistence.

The current compatibility catalog includes `accounts:read`, `accounts:sync`,
`positions:read`, `markets:read`, `risk:read`, `research:run` and `plans:create`.
This Story must not expose every catalog entry. `research:run` currently has no
stable `ResearchScope` validation contract, and `plans:create` is explicitly out
of the no-execution/read-only MCP scope.

## Existing Trading Core Contracts

`BrokerAccountController` exposes human-oriented account collection, detail and
read-only status routes under `/api/v1/broker-accounts`. Its implementation
casts the principal to `UserDto`, so agent access cannot be enabled by merely
passing an agent principal. A dedicated agent-aware application boundary is
required, with collection filtering before response construction.

`BrokerAccountReadOnlyStatusService` already performs owner-scoped lookup and
reads a broker snapshot. It does not currently evaluate `AgentGrant`.

`BrokerSynchronizationServiceImpl.synchronize(UUID ownerId, UUID brokerAccountId)`
already validates owner mapping, LIVE execution mode, provider registration,
external account identity, snapshot identity, freshness, collection availability
and authoritative account facts. The inspected code does not expose a public
Trading Core synchronization controller in the current route set; the
implementation plan must identify the smallest command boundary or leave the
MCP synchronization capability disabled if the existing human-approval policy
cannot be represented safely.

No current Trading Core controller was found that directly exposes broker
positions or risk status as an agent-ready public contract. The plan must adapt
existing dashboard/read models only where exact ownership and resource scope
can be enforced, and must not invent a broad proxy.

## Service Delegation Boundary

Trading Core calls Broker Service with a short-lived `X-Service-Authorization`
JWT issued by `ServiceJwtService`. Broker Service validates `type=service`,
issuer, audience and trusted keys, then creates a `ROLE_SERVICE` principal. The
Broker Service internal controllers require this service boundary and several
endpoints require a delegated actor for owner-scoped access.

Trading Core's current `BrokerServiceFeignConfiguration` derives a delegated
actor from a Feign `X-Delegated-Actor` template header or a human `UserDto`
principal. The current agent principal is not a `UserDto`, so agent calls would
currently emit a service token without a trusted owner actor unless a caller
sets the Feign header. Accepting an external `X-Delegated-Actor` header would be
a security defect. 0217 must derive delegation only from the validated agent
principal and encode it in the server-issued service JWT or an equivalent
validated internal context.

Broker Service must not receive or trust the external agent JWT, and must not
accept arbitrary external identity headers. Existing service authentication,
broker-account ownership mapping, snapshot safety and cTrader request guards
must remain authoritative.

## Configuration and Persistence

The MCP adapter currently documents `TRADING_OS_JWT` and the Gateway URL. Story
0217 will need an agent credential environment variable, a token-exchange path,
and safe in-memory token lifecycle documentation. No real value may be added to
source control or local shared runtime configuration.

Trading Core migration V56 is already merged and contains authentication audit
storage. A new migration is not justified by this analysis. If durable
agent-operation audit fields cannot reuse the existing authorization audit
boundary, the implementation plan must explicitly justify an additive migration
and isolated migration tests. No shared runtime database may be changed during
this Story.

## Initial Authorization Matrix

The implementation plan must confirm concrete routes before enabling them:

| Capability | Permission | Required resource validation | Initial decision |
|---|---|---|---|
| Account list/detail/status | `accounts:read` | exact owned `BROKER_ACCOUNT` per result | Candidate |
| Position read | `positions:read` | exact owned `BROKER_ACCOUNT` | Candidate; current public contract unclear |
| Market read | `markets:read` | canonical existing `MARKET` ID | Candidate; public market semantics need mapping |
| Risk read | `risk:read` | exact owned `BROKER_ACCOUNT` | Candidate; current public contract unclear |
| Account synchronization | `accounts:sync` | exact owned account plus existing safety/approval | Disabled until command boundary is proven safe |
| Research run | `research:run` | stable `RESEARCH_SCOPE` | Not enabled; scope contract absent |
| Plan creation | `plans:create` | broker account | Not enabled; outside read-only/no-execution scope |

## Required Implementation Decisions

The Implementation Plan must settle:

- exact MCP credential environment and in-memory token refresh contract;
- whether the existing token exchange must be amended for durable denial audit;
- explicit route/controller authorization adapter using `AgentAuthorizationService`;
- collection filtering and response-shape strategy without resource leakage;
- trusted agent-to-service delegation claim design under ADR-044;
- exact Gateway route behavior and whether Gateway should enforce only crypto
  validation while Trading Core enforces lifecycle/grants;
- concrete read-only operation mappings and unsupported-operation behavior;
- whether synchronization is safely exposable or remains disabled;
- audit fields and transaction/failure policy;
- isolated tests across MCP, Gateway, Trading Core and Broker Service.

## Risks and Controls

- **Human JWT fallback:** remove the MCP human-token requirement and test that no
  human token is accepted by agent exchange or agent-only paths.
- **Invalid-agent fallthrough:** explicitly reject invalid agent material where a
  valid human token is not intended, while preserving valid human behavior.
- **Cross-user leakage:** use owner-scoped queries and per-resource grant checks;
  never filter only after loading an unrestricted collection.
- **Forged delegation:** ignore client delegation headers and derive the actor
  from the validated principal in Trading Core.
- **Service confusion:** Broker Service accepts only service JWTs with the
  expected issuer/audience/type and never agent JWTs.
- **Revocation drift:** perform live agent, credential and grant checks on every
  protected operation.
- **Synchronization safety:** preserve cooldown, lease, budget, snapshot and
  human approval checks; do not call cTrader in tests.
- **Audit rollback:** use an independent audit transaction or explicit durable
  failure handling so denial evidence is not lost with a rejected operation.
- **MCP error leakage:** sanitize HTTP failures and never return credentials,
  JWTs, provider payloads or private ownership details.

## Validation Plan

- MCP client/server tests for credential exchange, in-memory token reuse,
  expiration, reauthentication, sanitized errors and exact exposed tools.
- Trading Core security tests for Story 0216 bypass cases, grants, ownership,
  collection filtering, human-only management and execution denial.
- Delegation tests proving agent context becomes a server-issued service claim
  and forged external headers are ignored.
- Broker Service tests for service-only authentication, delegated actor checks
  and rejection of agent JWTs or forged headers.
- Synchronization service/controller tests with mocked Broker Service and
  explicit assertions that cooldown/lease/snapshot guards remain active.
- Gateway route/filter tests for agent token handling and no human fallback.
- MCP adapter `pytest`, `python -m compileall -q app tests`, Java targeted tests,
  affected-module Maven tests and `git diff --check`.

## Workflow State

```text
Story 0217                 READ-ONLY IMPLEMENTATION IN PROGRESS
Repository Analysis        COMPLETED
Repository Analysis Gate   COMPLETED
Implementation Plan         APPROVED
Production Implementation   NO LIVE OPERATIONS
```
