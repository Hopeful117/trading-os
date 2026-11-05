# Implementation Report - Story 0207

## 1. Repository State

- Branch: `main`.
- Base HEAD at analysis: `491e9b6d`.
- No commit, push, merge or branch operation was performed.
- Existing Story 0207 artifacts and implementation changes remain uncommitted for
  human review.
- The worktree contained no unrelated user changes before implementation.

## 2. Approved Scope

The approved Repository Analysis and Implementation Plan limited this Story to
read-only MCP research capabilities. Controlled analytical commands remain
deferred because ADR-088 currently authorizes only read-only tools and the
existing active scan is account-scoped.

## 3. Changes

### MCP Capability

Added `get_active_scan(scan_id)` to `mcp-adapter/app/server.py`.

It maps only to:

```text
GET /api/v1/intelligence/scans/{scanId}
```

The input is a typed UUID. The Gateway remains responsible for authentication
and actor ownership. The adapter does not accept an account selector, start a
scan, cancel a scan or modify scan state.

The existing capabilities remain unchanged:

- `list_markets` -> `GET /api/v1/markets`;
- `get_market_thesis` -> `GET /api/v1/intelligence/market-theses/latest`;
- `get_opportunities` -> `GET /api/v1/opportunities`.

All responses remain source JSON. No Market Thesis state, evidence, provenance,
freshness, missingness, contradiction, opportunity score or active-scan status
is reinterpreted.

### Authentication and Safety

Added offline coverage for missing JWT configuration. Existing bearer propagation,
HTTPS enforcement, 401/403/404/5xx translation, timeout, network failure,
oversized response and token non-disclosure tests remain active.

No JWT parsing, refresh, browser extraction, persistence or logging was added.
No internal, provider, AI Engine, broker, execution, risk or write-capable MCP
operation was added.

### Documentation

Updated:

- `mcp-adapter/README.md` with the fourth read-only tool and deferred scan
  command boundary;
- `docs/investigations/mcp-first-agent-interaction-2026-11-05.md` with the
  active-scan projection capability;
- Story 0207 Repository Analysis and Implementation Plan.

Documentation update: required and completed. The public capability mapping and
read-only security boundary changed, while no Java or Gateway contract changed.

## 4. Validation

Executed:

```text
cd mcp-adapter && .venv/bin/pytest
```

Result: **29 passed**.

```text
cd mcp-adapter && .venv/bin/python -m compileall -q app tests
```

Result: passed.

```text
git diff --check
```

Result: passed.

The tests use the MCP SDK in-process and stdio transports with mocked Gateway
behavior. No Gateway, Kraken, cTrader, broker, LLM or remote MCP request was
made. A real JWT and authenticated local session were not available or used.

The additional active-scan fixtures cover `RUNNING`, `COMPLETED`, `FAILED`,
`CANCELLED` and `PARTIALLY_COMPLETED` states, provider/product identity,
diagnostics, empty opportunities and exact source-payload preservation. Gateway
403/404 ownership-denial behavior is also checked through the mocked HTTP path
and sanitized MCP errors, with absence of account or secret payloads. Empty and
incomplete projections are covered as well.

## 5. Live Validation

Not executed. The environment did not provide an explicitly authorized
short-lived Trading OS JWT, test account and local Gateway session. The final
live procedure remains:

1. start Gateway, Market Data and Market Intelligence;
2. provide a short-lived JWT through process environment only;
3. discover markets and select BTC/ETH by explicit market ID, provider and
   product type;
4. read Market Thesis and opportunities;
5. read an already-created, actor-authorized active-scan projection when a scan
   ID is supplied by an approved workflow.

Starting a scan is intentionally not part of this procedure.

## 6. Deferred Decisions

The following remain outside this implementation:

- `start_market_scan` or any other controlled analytical command;
- direct analysis submission, status, result or cancellation tools because the
  public analysis prefix is denied by Market Intelligence security;
- AI Engine interpretation retrieval;
- remote MCP transport, OAuth and external cTrader MCP integration.

Enabling controlled commands requires an ADR-088 amendment covering command
authorization, account selection, idempotency, limits, auditability and human
confirmation.

## 7. Vault Outcome

The Obsidian vault was not consulted during this Story. Repository and accepted
ADR evidence were sufficient. No vault action or proposal is required.

## 8. Result

The adapter now supports a truthful read-only research workflow over canonical
markets, deterministic Market Thesis, opportunities and existing active-scan
projections. It remains independent of OpenCode and does not become a source of
trading truth or an autonomous analytical command executor.
