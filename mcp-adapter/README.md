# Trading OS MCP Adapter

Local, read-only MCP adapter for OpenCode and compatible MCP clients.

## Scope

The adapter runs over stdio and calls only public Trading OS Gateway GET
contracts. It exposes exactly:

- `list_markets`
- `get_active_scan`
- `get_market_thesis`
- `get_opportunities`

`get_active_scan` reads an already-created, Gateway-authorized active-scan
projection. It does not start, cancel or modify a scan.

It cannot create Trade Plans, evaluate Risk, modify positions, place orders,
call cTrader, call providers directly, access internal service endpoints or
submit analysis executions.

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
header and never returned by a tool. Remote Gateway URLs must use HTTPS.

## Launch

```bash
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080 \
TRADING_OS_JWT="$TRADING_OS_JWT" \
.venv/bin/trading-os-mcp
```

The process uses stdio and does not listen on a TCP port.

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

The `opencode mcp add` command was not executed automatically because it would
modify user configuration and require a real JWT.

Do not place a real JWT in repository configuration. Starting a market scan is
not exposed by this adapter because it is a controlled analytical command and
requires a separately approved authorization boundary. Remote MCP HTTP
transport, MCP OAuth and external cTrader MCP servers are not implemented or
authorized.
