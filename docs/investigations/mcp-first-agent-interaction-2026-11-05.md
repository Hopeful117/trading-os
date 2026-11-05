# Investigation - MCP-First Agent Interaction

## Status

COMPLETE FOR LOCAL READ-ONLY PROTOTYPE; ARCHITECTURE APPROVED

## Findings

### Existing capability surface

- `GET /api/v1/markets` is a public Market Data read routed by the Gateway.
- `GET /api/v1/intelligence/market-theses/latest` is a user-authenticated
  Market Intelligence read and exposes state, scenario, evidence, freshness,
  provenance and fingerprint.
- `GET /api/v1/opportunities` is a trader-facing Market Intelligence read.
- `POST /api/v1/intelligence/analyses`, scan creation, trade-plan creation,
  risk evaluation and execution are intentionally excluded from the prototype.
- `/internal/**` is not routed by the Gateway and is reserved for service
  contracts; MCP must not call it.

### Architecture comparison

| Option | Result |
|---|---|
| Application-first | Useful for Angular workflows, but not reusable as the agent boundary. |
| MCP-first direct-to-services | Unsafe and duplicates authorization. |
| Hybrid with MCP-first adapter over Gateway | Recommended for the proposal and prototype. |

The adapter is not a new business service. It is a local protocol adapter that
translates MCP calls into existing public contracts.

### Security findings

ADR-021 requires authenticated, authorized and traced service communication for
AI Engine calls; it also forbids exposing user JWTs to the AI Engine. The MCP
adapter is not the AI Engine and therefore may use a user JWT for the local
Gateway session, but the token must remain process configuration and must not be
forwarded to Python AI or provider integrations.

The historical API audit identified heterogeneous legacy surfaces and ownership
risks. Those findings reinforce the choice to expose only the narrow, reviewed
Gateway contracts above rather than automatically wrapping every endpoint.

### cTrader integration findings

Official cTrader documentation confirms OAuth scopes distinguish view-only
`accounts` access from full `trading` access. Existing ADR-073/074/077 assign
trading/account authority to Broker Service and market-data authority to Market
Data. The MCP layer must consume normalized facts and must not own cTrader
credentials, tokens, account binding or subscriptions.

No external cTrader MCP server was installed or connected. That remains a human
approval and security review decision.

## Prototype

`mcp-adapter/` provides a local stdio MCP server with three read-only tools:

- `list_markets`
- `get_market_thesis`
- `get_opportunities`

Configuration:

```text
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080
TRADING_OS_JWT=<short-lived user JWT>
TRADING_OS_HTTP_TIMEOUT_SECONDS=10
```

The adapter uses the official MCP Python SDK and `httpx`. It does not bind a
port, persist credentials, call providers, create plans, evaluate risk or
execute orders.

## OpenCode Configuration Guide

OpenCode `1.18.35` is installed. Its CLI exposes `opencode mcp add [name]` and
supports local server environment variables. The local registration entry is:

```json
opencode mcp add trading-os
# local command: <repository>/mcp-adapter/.venv/bin/trading-os-mcp
```

The JWT must be injected by the operator's environment or secret manager. It
must not be committed to repository configuration.

## Validation

The unit test covers the Gateway URL and bearer header construction without
network access. Full MCP protocol validation requires installing the declared
Python dependency and running the server under an MCP Inspector/client. No
external service or real broker call is required.

## Limitations and Required Decisions

- The current prototype has no remote HTTP transport or MCP OAuth server.
- Tool outputs currently pass through public JSON and need future explicit
  output redaction/size policy before wider deployment.
- A dedicated MCP user/session scope and token audience should be approved
  before remote deployment.
- OpenCode configuration syntax must be confirmed against the installed client.
- External cTrader MCP integrations remain unevaluated operationally and are not
  approved for installation.
