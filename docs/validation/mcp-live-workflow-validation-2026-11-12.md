# MCP LIVE Workflow Validation

Date: 2026-11-12
Status: `BLOCKED`
Verdict: `BLOCKED`

## Scope

Restore the existing OpenCode MCP session and validate read-only access to the
existing FTMO Free cTrader account through the standard Trading OS LIVE runtime.
No broker mutation, approval challenge, or execution continuation is allowed in
this validation.

## Runtime Topology

| Component | Runtime | Evidence |
| --- | --- | --- |
| OpenCode MCP configuration | Standard LIVE target after correction | `.opencode/opencode.json` now points to `http://127.0.0.1:17080` |
| MCP bridge | Local process, `127.0.0.1:17891` | `python -m app.bridge`, PID observed during diagnosis |
| Standard Gateway | Existing runtime | `127.0.0.1:17080`, HTTP `401` without authentication |
| E2E Gateway | Separate synthetic runtime | `127.0.0.1:27080`, not the target for FTMO LIVE validation |
| Standard Trading Core | Existing runtime | Docker service `trading-app`, port `17081` |
| E2E Trading Core | Synthetic runtime | Docker service `trading-os-e2e-trading-app`, port `27081` |

The E2E runtime contains synthetic `e2e-ctrader-account` records and must not be
used as evidence for the FTMO Free LIVE account.

## Authentication Diagnosis

The active bridge loaded its opaque credential from
`~/.config/trading-os/mcp-agent-credential`. The file is owner-only but contains
17 bytes, whereas generated agent credentials are 43-character URL-safe values.
Its SHA-256 digest matched no credential in either the standard or E2E database.
The credential exchange returned HTTP `401` against both Gateways.

Relevant backend evidence:

- Standard Gateway logged `POST /api/v1/agents/token` as `401 UNAUTHORIZED`.
- E2E Gateway logged the same token exchange as `401 UNAUTHORIZED`.
- The standard Gateway previously logged successful token exchange and
  successful reads for broker account `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`,
  proving that the route and agent authentication implementation can work.
- Direct unauthenticated requests returning `401` are expected Spring Security
  behavior and do not indicate a broken Gateway.

Root cause: the local MCP credential is stale or invalid and is not present as
an active credential in either runtime. The broker-account read failures are a
downstream consequence of failed token exchange, not account authorization or
market-data failure.

`list_markets` also has a previously documented secondary integration risk: an
authenticated unfiltered catalog can exceed the MCP client's 262,144-byte
response limit. That is not the current failure because authentication fails
first, including for the bounded `EUR/USD` request. After reconnecting against
the standard runtime, validate the filtered market request before attempting an
unfiltered catalog request.

## Account And Grant Evidence

The standard Trading Core database contains:

- broker account `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`;
- display name `FTMO Free cTrader`;
- external account id `49411967`;
- connection status `CONNECTED`;
- `ExecutionMode.LIVE`;
- active OpenCode agent identity;
- active account grants for `accounts:read`, `positions:read`, `scans:run`,
  `plans:create`, `trade-plan-risk:evaluate`, and related read permissions.

No account, grant, credential, risk limit, or broker configuration was changed.

## Corrections Applied

- Corrected `.opencode/opencode.json` from the synthetic E2E Gateway (`27080`)
  to the existing standard LIVE Gateway (`17080`).
- No security check was disabled.
- No credential value was printed or stored in the repository.
- No synthetic environment was created or removed.

## MCP Validation Results

Blocked before valid MCP reads could run:

- tool discovery: bridge process was registered, but the credential exchange
  failed for tool calls;
- `list_markets`: blocked by failed agent authentication;
- `list_authorized_accounts`: blocked by failed agent authentication;
- account details, status, balance/equity and positions: not executed through a
  valid MCP session;
- FTMO provider and canonical market evidence: not retrievable through MCP;
- opportunity, Trade Plan and T0 risk evaluation: not started.

## Required Human Step

Open the authenticated standard Trading OS web application at
`http://127.0.0.1:17085`, open Agents, select the existing active `OpenCode`
agent, and use the supported Connect action. This issues a fresh credential and
sends it to the local bridge without displaying it. Restart OpenCode afterward
so the corrected MCP configuration is loaded.

During this validation, the Connect action was attempted from the authenticated
interface. Credential issuance succeeded, but the currently running bridge was
still the process started with the old `27080` environment and returned HTTP
`400` from `/v1/connect`; the credential was therefore not persisted. No
additional credential issuance was attempted after this diagnosis.

This step is credential provisioning through the existing application contract;
it does not change the FTMO broker account or place an order.

## Safety Result

No order was created, amended, cancelled, or closed. No broker synchronization,
Trade Plan, risk evaluation, approval challenge, or execution continuation was
performed. The FTMO account remains an external-broker `LIVE` account; it was
not treated as internal `PAPER`.
