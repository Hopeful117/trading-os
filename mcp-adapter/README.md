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
- `inspect_historical_research_readiness`
- `request_historical_dataset_export`
- `get_historical_dataset_export_status`

`get_active_scan` reads an already-created, Gateway-authorized active-scan
projection. It does not start, cancel or modify a scan.

`start_market_research` submits an explicit research-scope and two-market request
to the public Gateway. A human owner creates and grants the exact scope before
the agent call. The Java Market Intelligence workflow owns authorization,
deterministic evidence assembly, lifecycle persistence and report generation.
The client must supply a stable `idempotency_key` and reuse it for retries.
Poll the returned research ID with `get_research_status`, then retrieve the
persisted report with `get_research_report`.

`inspect_historical_research_readiness` returns bounded deterministic evidence,
provenance, cutoff and population diagnostics for an explicitly granted
historical research scope. It does not return arbitrary historical rows.

`request_historical_dataset_export` submits a bounded, idempotent export request
using the existing Java dataset contract. The dataset is written to the
server-controlled Market Intelligence export boundary; MCP returns only the
job identifier and lifecycle metadata. Use
`get_historical_dataset_export_status` to retrieve authorized status, checksum,
dataset version and validation metadata. No absolute filesystem path or dataset
rows are returned.

Before using the historical tools, an authorized human administrator must use
the existing Trading OS agent-management flow to create an owner-scoped
two-market research scope with explicit historical limits, then create separate
active grants for `research:inspect` and, when required, `research:export` on
that exact scope. Existing agents are not granted these permissions
automatically. Do not edit database permissions or place credentials in MCP
configuration.

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

## Direct Launch (diagnostics)

```bash
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080 \
TRADING_OS_AGENT_CREDENTIAL="<credential only for local diagnostics>" \
.venv/bin/trading-os-mcp
```

The direct process uses local stdio and does not listen on a TCP port. Normal
OpenCode usage should use the bridge below instead of this diagnostic launch.

## OpenCode

The repository includes `.opencode/opencode.json`, which starts the local bridge
automatically as an OpenCode local MCP server. Restart OpenCode after installing
the adapter so it loads this configuration.

The bridge listens only on `127.0.0.1:17891` and exposes a short-lived pairing
challenge to the authenticated Trading OS web application. The Agents page
issues the credential and sends it directly to the bridge; it is never rendered
or copied by the user. The bridge validates the credential through the Gateway,
keeps it only in process memory, and exposes the existing read-only MCP tools.

For installations without the repository configuration, the equivalent local
server registration is:

```text
opencode mcp add trading-os
# local command: .venv/bin/python -m app.bridge
# working directory: <repository>/mcp-adapter
```

The bridge process requires no credential environment variable. Its local
pairing endpoint accepts only configured browser origins and each challenge is
single-use with a short expiry.

Configure only the Gateway and bridge settings:

```bash
export TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080
export TRADING_OS_MCP_BRIDGE_PORT=17891
```

The MCP server is named `trading-os-research`. The adapter does not persist
credentials, access tokens, research state or reports.

Do not place a real credential or token in repository configuration. Starting an
active market scan is not exposed by this adapter. Execution, broker
credentials, grant administration and internal service calls are not exposed.
Remote MCP HTTP transport, MCP OAuth and external cTrader MCP servers are not
implemented or authorized.
