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
TRADING_OS_JWT=<short-lived user JWT>
TRADING_OS_HTTP_TIMEOUT_SECONDS=10
TRADING_OS_MAX_RESPONSE_BYTES=262144
```

`TRADING_OS_JWT` is read from the process environment, sent only as a bearer
header and never returned by a tool. The Gateway remains responsible for JWT
authentication and actor ownership. Remote Gateway URLs must use HTTPS.

## Launch

```bash
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080 \
TRADING_OS_JWT="$TRADING_OS_JWT" \
.venv/bin/trading-os-mcp
```

The process uses local stdio and does not listen on a TCP port. The adapter does
not persist JWTs, research state or reports.

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
export TRADING_OS_JWT='<short-lived user JWT>'
```

The MCP server is named `trading-os-research`. The `opencode mcp add` command
was not executed automatically because it would modify user configuration and
require a real JWT.

Do not place a real JWT in repository configuration. Starting an active market
scan is not exposed by this adapter. Remote MCP HTTP transport, MCP OAuth and
external cTrader MCP servers are not implemented or authorized.
