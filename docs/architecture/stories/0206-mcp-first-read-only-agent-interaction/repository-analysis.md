# Repository Analysis - Story 0206

## Story Understanding

Story 0206 requests a local, read-only MCP interaction boundary for OpenCode
and compatible clients. The implementation must run as a stdio subprocess,
translate exactly three MCP tools into existing public Gateway requests, and
preserve the semantics and ownership of the returned Trading OS data.

The Story includes:

- the `mcp-adapter/` Python package;
- official MCP SDK integration over stdio;
- `list_markets`, `get_market_thesis` and `get_opportunities`;
- Gateway URL, short-lived JWT and bounded timeout configuration;
- typed and bounded tool schemas;
- offline MCP and HTTP contract tests;
- OpenCode configuration and security documentation.

The Story explicitly excludes remote MCP HTTP deployment, MCP OAuth, external
MCP installation, cTrader integration, AI Engine calls, Angular changes,
TradePlan creation, Risk evaluation, position mutation and execution.

## Repository Summary

Trading OS is a multi-service Spring Boot platform with a Spring Cloud Gateway
as the public HTTP entry point. The relevant public routes are:

- `/api/v1/markets/**` to Market Data;
- `/api/v1/intelligence/**` to Market Intelligence;
- `/api/v1/opportunities/**` to Market Intelligence.

The Gateway requires authentication for all non-exempt routes. Market Data
allows public `GET /api/v1/markets/**`, while Market Intelligence requires a
user JWT for `/api/v1/intelligence/**` and `/api/v1/opportunities/**`.

The repository already contains a local Python `ai-engine`, but this Story is
not an AI Engine change. The MCP adapter is a separate local protocol adapter
and must not communicate directly with the AI Engine or Java repositories.

## Affected Modules

### `mcp-adapter/`

This is the direct Story surface. It currently contains:

- `pyproject.toml` with Python packaging, `httpx`, the official MCP SDK and
  pytest optional dependencies;
- `app/server.py` with the three MCP tools and stdio entry point;
- `app/client.py` with Gateway URL, JWT header and HTTPS validation;
- `tests/test_client.py` with an offline bearer-header smoke test.

### `gateway/`

The Gateway owns the public route and authentication boundary. The MCP adapter
must treat its public contracts as the only downstream entry point and must not
call service-discovery or internal service URLs.

Relevant files include:

- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`;
- `gateway/src/main/java/com/hope/trading/gateway/security/SpringSecurityConfig.java`;
- `gateway/src/test/java/com/hope/trading/gateway/GatewayMarketIntelligenceRouteTest.java`.

### `market-data/`

Market Data owns the canonical market catalogue. `MarketController` exposes
`GET /api/v1/markets`, and `MarketDataSecurityConfiguration` permits the public
GET market surface while protecting synchronization, subscriptions and internal
endpoints.

### `market-intelligence/`

Market Intelligence owns deterministic Market Thesis and trader-facing
opportunity projections. Relevant controllers are:

- `MarketThesisController` at
  `/api/v1/intelligence/market-theses`;
- `OpportunityController` at `/api/v1/opportunities`;
- `MarketIntelligenceController` for analysis and scan lifecycle reads/writes.

`MiSecurityConfiguration` requires authenticated user context for the public
intelligence and opportunity surfaces, while `/internal/v1/**` requires a
service authority and other internal paths are denied.

### `broker-service`, `trading-core`, `ai-engine`

These modules are not implementation targets. They remain relevant only as
boundaries that the adapter must not cross. Broker Service owns provider and
credential concerns, Trading Core owns account/risk/execution workflows, and
AI Engine remains a separately governed analysis service.

## Existing Implementation

### Gateway Contracts

`GatewayRouteConfig` routes the selected public contracts without path rewrite:

- `/api/v1/markets/**` to `market-data`;
- `/api/v1/opportunities/**` to `market-intelligence`;
- `/api/v1/intelligence/**` to `market-intelligence`.

The route table deliberately does not route `/internal/**`. This supports the
Story requirement that MCP cannot use service-only APIs.

`SpringSecurityConfig` authenticates all non-exempt Gateway exchanges. The local
adapter therefore acts as an authenticated Gateway client, not as a replacement
for service authorization.

### Market Data Read

`MarketController.findAll()` maps the persisted market catalogue to
`MarketResponse` values through `MarketMapper`. The current endpoint does not
define a `productType` query filter, so the adapter must not advertise or send a
filter that the contract does not support.

### Market Thesis Read

`MarketThesisController.latest()` requires a `MiUserPrincipal` and accepts
`marketId`, `instrument` and `direction`. It returns `MarketThesisResponse`,
which preserves:

- thesis and lineage identity;
- version and market identity;
- state and scenario;
- all, supporting, contradicting and missing evidence;
- reasons, timestamps and validity;
- analysis execution, composer version, input fingerprint and supersession.

`MarketThesis` is immutable deterministic evidence. The adapter must return it
without turning `CANDIDATE` into approval or interpretation.

### Opportunity Read

`OpportunityController.search()` returns a bounded page with a maximum size of
200 at the current HTTP contract. The adapter applies a stricter MCP limit of
50 and uses the existing `instrument`, `activeOnly`, pagination and sort query
parameters. The response can include strategy match, scan, analysis and Market
Thesis references, but it remains a trader-facing candidate and not an
execution authorization.

### Current Adapter Behavior

`app/client.py` creates an `Authorization: Bearer` header, serializes successful
JSON responses and rejects non-HTTPS remote Gateway URLs. It requires
`TRADING_OS_JWT` and defaults the local Gateway to `http://127.0.0.1:17080`.

`app/server.py` registers exactly three tools and calls only the three public
Gateway paths described by the Story. It does not register write-capable tools,
resources, prompts or network listeners.

The implementation is present as a prototype, but its runtime dependencies are
not installed in the current environment. The repository currently has only a
single adapter smoke test and no MCP protocol-level test.

## Relevant Documentation

- `AGENTS.md`;
- `README.md`;
- `docs/architecture/adr/ADR-020.md`;
- `docs/architecture/adr/ADR-021.md`;
- `docs/architecture/adr/ADR-028.md`;
- `docs/architecture/adr/ADR-030.md`;
- `docs/architecture/adr/ADR-034.md`;
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`;
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`;
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`;
- `docs/architecture/adr/ADR-077-ctrader-market-data-transport-boundary.md`;
- `docs/architecture/adr/ADR-088-mcp-first-agent-interaction-boundary.md`;
- `docs/investigations/mcp-first-agent-interaction-2026-11-05.md`;
- `docs/architecture/stories/0206-mcp-first-read-only-agent-interaction/story.md`;
- Context7 documentation for the official MCP specification and Python SDK;
- Official cTrader Open API authentication documentation.

DevLog was queried for bounded Story 0206 context. The response completed but
was truncated and did not retain the requested Story, ADR, investigation or
adapter file contents. Its only useful retained evidence was the general human
authority boundary from `AGENTS.md`. The repository is therefore authoritative
for this analysis.

The Obsidian vault was not consulted. No transverse note was required to resolve
the current repository and architecture boundaries.

## Constraints

- ADR-088 is accepted, but its scope remains limited to a local read-only stdio
  adapter.
- The Story's current status is `Story approved - Repository Analysis required`;
  this analysis does not authorize planning or implementation.
- The Gateway remains the public authentication and routing boundary.
- A user JWT may be supplied to the local adapter but must not be logged,
  persisted, returned or sent to providers or the AI Engine.
- Remote Gateway URLs must use HTTPS.
- The adapter must use only existing public Gateway contracts.
- `/internal/**`, service discovery, repositories, databases and provider APIs
  are forbidden dependencies.
- Market Data remains authoritative for market facts and Market Intelligence
  remains authoritative for Market Thesis and opportunities.
- Market Thesis state, evidence, freshness, provenance and missingness must be
  preserved without semantic promotion.
- No Risk, TradePlan, Execution, Broker Service, cTrader or AI Engine behavior
  may change.
- Automated validation must be offline and must not call external providers,
  brokers, cTrader, LLMs or remote MCP servers.
- OpenCode configuration must be validated against the installed client version
  rather than assumed from generic MCP examples.
- The repository must not receive secrets or provider credentials.

## Risks

### MCP SDK/runtime compatibility

The prototype declares the official MCP Python SDK, but it is not installed in
the current environment. SDK import/API compatibility and actual stdio session
behavior remain unverified until the declared dependencies are installed in an
isolated environment.

### Token handling

The local subprocess receives a user JWT through its environment. This is
acceptable for the approved local prototype but creates operator-level exposure
and is not a suitable remote authentication model. Remote deployment therefore
remains correctly deferred.

### Public contract drift

The adapter depends on the Gateway paths and JSON projections of Market Data and
Market Intelligence. Contract changes could silently affect agent behavior if
there are no protocol-level and response-shape tests.

### Output size and sensitive projections

Market Thesis and Opportunity responses contain evidence and provenance. The
adapter currently serializes the public JSON response without an explicit MCP
output-size or field-redaction policy. This is acceptable only for the narrow
local prototype and must be addressed before wider deployment.

### Authentication asymmetry

Market reads are public at the Market Data service, while Market Intelligence
reads require a user principal. The adapter must retain one consistent Gateway
authentication path and must not infer that public market data authorizes access
to user-specific intelligence.

## Open Questions

- Which exact OpenCode version and configuration schema will be used for the
  local MCP registration?
- Should a future remote MCP boundary use a dedicated token audience/scope or a
  separate identity provider?
- What explicit output-size and field-redaction policy is required before the
  adapter is used outside local development?

These questions do not block planning for the approved local prototype. They do
block any remote or wider deployment scope.

## Recommendation

Ready for planning

The repository is sufficiently understood for an Implementation Plan. Ownership
is clear, ADR-088 is compatible with the existing service boundaries, and no
blocking prerequisite exists for the local prototype. The missing Python
dependencies are a validation-environment limitation, not an architecture
blocker.

## Implementation Readiness

The Story can be implemented using the current repository. The public Gateway
contracts already exist, the selected downstream DTOs preserve the required
analytical semantics, and no database or service contract change is required.

The remaining technical prerequisites are dependency installation in an isolated
Python environment and protocol-level MCP tests. Remote MCP authorization,
external cTrader integration and wider output governance are intentionally not
prerequisites because they are out of scope.

No missing ownership, data source, persistence boundary or blocking ADR conflict
was identified.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
