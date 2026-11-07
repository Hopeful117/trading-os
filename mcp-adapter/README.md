# Trading OS MCP Adapter

Local MCP adapter for OpenCode and compatible MCP clients. It exposes direct
application reads and a separately bounded Trading OS-owned research lifecycle.

## Scope

The adapter runs over stdio and calls only public Trading OS Gateway contracts.
It exposes two capability families.

Direct application tools:

- `list_markets`
- `get_active_scan`
- `get_market_thesis`
- `get_opportunities`
- `list_authorized_accounts`
- `get_authorized_account`
- `get_authorized_account_status`
- `get_authorized_positions`

Specialized research tools:

- `start_market_research`
- `get_research_status`
- `get_research_report`

`get_active_scan` reads an already-created, Gateway-authorized active-scan
projection. It does not start, cancel or modify a scan.

`start_market_research` submits an explicit two-market research request to the
public Gateway. The Java Market Intelligence workflow owns authorization,
deterministic evidence assembly, lifecycle persistence and report generation.
The client must supply a stable `idempotency_key` and reuse it for retries.
Poll the returned research ID with `get_research_status`, then retrieve the
persisted report with `get_research_report`.

The account tools call the dedicated `/api/v1/agent/**` Trading Core boundary.
Trading Core filters account collections before building the response and
requires an exact, active `AgentGrant` for every broker-account resource.
Positions are scoped to the same broker-account grant and never accept an owner
or delegated-actor parameter from MCP.

The adapter cannot create Trade Plans, evaluate Risk, modify positions, place
orders, call cTrader, call providers directly, access internal service
endpoints, invoke the AI Engine directly or submit technical analysis
executions. Research output is advisory and never authorizes a trade.

## Install and Test

```bash
cd mcp-adapter
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
.venv/bin/pytest
```

## Configuration

```text
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080
TRADING_OS_AGENT_CREDENTIAL=<opaque agent credential>
TRADING_OS_HTTP_TIMEOUT_SECONDS=10
TRADING_OS_MAX_RESPONSE_BYTES=262144
```

`TRADING_OS_AGENT_CREDENTIAL` is exchanged through the public
`POST /api/v1/agents/token` Gateway contract. The resulting short-lived agent
token is shared by the tools for the lifetime of the MCP process, kept only in
process memory, refreshed once after an HTTP 401, and
never returned by a tool. Human JWTs are deliberately not accepted by this
adapter. Remote Gateway URLs must use HTTPS.

## Launch

```bash
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080 \
TRADING_OS_AGENT_CREDENTIAL="$TRADING_OS_AGENT_CREDENTIAL" \
.venv/bin/trading-os-mcp
```

The process uses local stdio and does not listen on a TCP port. The adapter does
not persist credentials, access tokens, research state or reports.

## OpenCode

Register the adapter using the MCP configuration syntax supported by the
installed OpenCode version. With OpenCode `1.18.35`, the supported CLI entry
point is:

```bash
opencode mcp add trading-os
```

When prompted for a local command, use:

```text
<repository>/mcp-adapter/.venv/bin/trading-os-mcp
```

Configure these variables through the OpenCode local-server environment
settings, or export them before launching the client:

```bash
export TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080
export TRADING_OS_AGENT_CREDENTIAL='<opaque agent credential>'
```

The MCP server is named `trading-os-research`. The `opencode mcp add` command
was not executed automatically because it would modify user configuration and
require a real JWT.

Do not place a real credential or token in repository configuration. Starting an
active market scan is not exposed by this adapter. Execution, broker
credentials, grant administration and internal service calls are not exposed.
Remote MCP HTTP transport, MCP OAuth and external cTrader MCP servers are not
implemented or authorized.
