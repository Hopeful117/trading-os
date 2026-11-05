# Story 0206 - MCP-First Read-Only Agent Interaction

## Metadata

**ID:** `0206`
**Title:** MCP-First Read-Only Agent Interaction
**Status:** Implementation complete - human delivery review required
**Priority:** P1 - Trading Intelligence Interaction
**Approach:** Local stdio MCP adapter over existing public Gateway contracts.
**Related ADR:** `ADR-088`

## Mission

Provide a reusable, safe interaction boundary for OpenCode and compatible MCP
clients without making Angular the only interface for agent-assisted market
investigation.

The first implementation is a local MCP server launched by the client as a
stdio subprocess. It translates a deliberately small set of read-only MCP tools
into existing authenticated or public Trading OS Gateway requests.

The adapter is an anti-corruption layer, not a new business service and not a
second source of domain truth.

## Repository and ADR Review

Before implementation:

- Inspect Git status, branch and HEAD.
- Read repository `AGENTS.md`.
- Read ADR-020, ADR-021, ADR-028, ADR-030, ADR-034, ADR-073, ADR-074,
  ADR-076, ADR-077 and accepted ADR-088.
- Read the MCP-first investigation from 2026-11-05.
- Inspect the public Gateway route table and the exact downstream DTOs.
- Verify that the selected contracts preserve ownership, provenance, freshness,
  missingness and analytical state.
- Verify that no internal endpoint or provider credential is required.

Do not invent a second Market Intelligence, Risk, Broker or AI Engine
architecture. Reuse existing public contracts and service authorities.

## Scope

### Local MCP Adapter

Maintain a small Python package under `mcp-adapter/` using the official MCP
Python SDK and `httpx`.

The initial server uses stdio transport and must not listen on a network port.
It receives the Gateway URL and a short-lived user JWT through environment
configuration:

```text
TRADING_OS_GATEWAY_URL
TRADING_OS_JWT
TRADING_OS_HTTP_TIMEOUT_SECONDS
```

Remote non-local Gateway URLs must use HTTPS. The JWT must be sent only in the
`Authorization: Bearer` header and must never be logged, returned by a tool, or
placed in a URL query parameter.

### Initial MCP Tools

Implement exactly these read-only tools:

| Tool | Existing contract | Result |
|---|---|---|
| `list_markets` | `GET /api/v1/markets` | Canonical market catalogue |
| `get_market_thesis` | `GET /api/v1/intelligence/market-theses/latest` | Latest deterministic Market Thesis |
| `get_opportunities` | `GET /api/v1/opportunities` | Bounded trader-facing opportunity page |

Tool schemas must use typed arguments and bounded limits. Outputs must preserve
source JSON semantics, including identifiers, analytical state, evidence,
freshness, provenance, contradictions and missingness.

The adapter must not turn:

- `CANDIDATE` into an approval or recommendation;
- an opportunity score into a probability;
- a market read into a TradePlan;
- a Market Thesis into a Risk verdict;
- a tool call into an execution command.

### Gateway Boundary

All calls must go through the public Gateway URL. The adapter must not call:

- `/internal/**` endpoints;
- service-discovery addresses;
- Java repositories or databases;
- Kraken, cTrader or other provider APIs;
- the AI Engine directly;
- broker credentials or tokens.

## Security and Safety

- The adapter is read-only by construction; no write-capable MCP tool is
  registered.
- No TradePlan, Risk, credential, broker, position mutation, order or execution
  operation is in scope.
- Existing Gateway authentication and downstream ownership checks remain
  authoritative.
- Secrets must be supplied through environment or an approved local secret
  mechanism and excluded from repository configuration and logs.
- HTTP failures, authentication failures and oversized responses must produce
  controlled tool errors without exposing headers, tokens or provider payloads.
- The adapter must not automatically refresh, mint or persist Trading OS JWTs.
- No remote Streamable HTTP MCP endpoint is implemented by this Story.
- No external cTrader MCP server is installed, configured or connected.

## Out of Scope

- Remote MCP deployment or MCP OAuth/resource-server implementation.
- OpenCode product changes or undocumented client configuration changes.
- Installation or connection of external MCP servers.
- cTrader MCP integration, cTrader credentials or provider-specific DTOs.
- Direct Market Intelligence, Market Data, Trading Core or Broker Service
  changes unless a public contract defect blocks the adapter and is separately
  approved.
- New domain capabilities, persistence, caching, event streaming or polling.
- Trade-plan creation, risk evaluation, human decisions or execution.
- AI Engine invocation or LLM calls.
- Angular implementation.

## Testing

### Adapter Unit Tests

Add offline tests for:

- Gateway URL construction;
- bearer-header construction without token leakage;
- HTTPS enforcement for non-local Gateway URLs;
- bounded opportunity parameters;
- typed Market Thesis arguments;
- HTTP 401/403/404/5xx translation;
- timeout handling;
- malformed JSON handling;
- absence of write-capable tools;
- absence of provider or internal endpoint calls.

### MCP Contract Tests

Using the official SDK or MCP Inspector in an offline fixture:

- start the server through stdio;
- initialize the MCP session;
- list tools;
- verify exactly the approved tools and schemas;
- invoke each tool against a mocked Gateway;
- verify JSON-RPC responses and bounded outputs.

### Repository Validation

Run the adapter tests, `git diff --check`, and any repository-standard Python
quality checks available for the new package. Do not call Trading OS, Kraken,
cTrader, an LLM provider or a remote MCP server during automated validation.

## Runtime Validation

Prepare a local-only validation procedure that demonstrates:

1. A client launches the adapter as a stdio subprocess.
2. The adapter advertises only the three approved tools.
3. A mocked or local Gateway receives only public read requests.
4. The bearer token is transmitted only as an HTTP header.
5. Market Thesis state and evidence are returned without reinterpretation.
6. No write, broker or execution request is emitted.

An actual Gateway session using a real user JWT requires explicit human approval
of the environment, token source and test data. No broker or cTrader validation
is required for this Story.

## Documentation

Document:

- local installation and launch commands;
- environment variables and secret handling;
- OpenCode client configuration for the installed version;
- tool schemas and Gateway mappings;
- read-only and ownership guarantees;
- failure and timeout behavior;
- known limitations;
- the boundary between this adapter and future remote MCP authorization.

## Acceptance Criteria

- A local stdio MCP server starts from `mcp-adapter/`.
- The server exposes exactly `list_markets`, `get_market_thesis` and
  `get_opportunities`.
- Each tool calls only the documented public Gateway contract.
- No `/internal/**`, provider, repository, database, broker or AI Engine call
  is possible through the adapter.
- The adapter requires a configured JWT and never logs or returns it.
- Non-local Gateway URLs require HTTPS.
- Opportunity result size is bounded by the tool schema.
- Market Thesis analytical state, evidence, provenance, freshness and
  missingness are preserved without semantic promotion.
- No tool creates or modifies a TradePlan, Risk decision, position, order or
  execution intent.
- Offline tests validate MCP initialization, tool listing and mocked Gateway
  calls.
- No external MCP server is installed or connected.
- Remote MCP deployment remains explicitly out of scope.
- `ADR-088` remains consistent with the implementation.

## Implementation Report

The final report must cover:

1. Repository state and approved Story/ADR context.
2. Adapter structure and MCP SDK version.
3. Gateway contracts reused.
4. Tool schemas and output semantics.
5. Authentication and secret-handling controls.
6. Proof that write, internal and provider calls are excluded.
7. OpenCode configuration tested against the installed client version.
8. Tests and validation commands executed.
9. Runtime validation limitations.
10. Remaining decision required before remote MCP or cTrader integration.

Do not commit, push or merge without explicit authorization.
