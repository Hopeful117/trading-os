# Implementation Plan - Story 0206

## Overview

Complete and validate the existing local `mcp-adapter/` prototype as a small,
read-only MCP stdio server. The adapter will expose exactly three typed tools
and translate them into existing public Gateway GET requests. It will not add a
Gateway route, change a Java service, add persistence or introduce a remote MCP
transport.

The plan keeps the Gateway as the authentication and ownership boundary and
keeps Market Data and Market Intelligence as the authorities for their returned
contracts. It also adds protocol-level tests so the current static prototype is
validated as an MCP server rather than only as an HTTP helper.

## Planned Changes

### 1. Confirm the Python MCP runtime contract

Affected component: `mcp-adapter` packaging and server entry point.

Verify the declared official MCP SDK version and its current server import,
tool-registration and stdio APIs. Keep the entry point under the module main
guard so importing the server in tests does not start a blocking process.

Constraint: use the official SDK and local stdio only; do not add a network
listener or a second protocol implementation.

### 2. Harden the Gateway client boundary

Affected component: `mcp-adapter/app/client.py`.

Keep Gateway URL, JWT and timeout configuration environment-backed. Add the
smallest required error and response handling needed for controlled MCP tool
errors, bounded response size and secret-free diagnostics. Preserve the HTTPS
requirement for non-local Gateway URLs and the bearer-header-only token flow.

Constraint: the client may issue only GET requests to the approved Gateway
public surface. It must not mint, refresh, persist or log JWTs.

### 3. Finalize the three MCP tools

Affected component: `mcp-adapter/app/server.py`.

Keep exactly these tools and schemas:

- `list_markets()` -> `GET /api/v1/markets`;
- `get_market_thesis(market_id, instrument, direction)` ->
  `GET /api/v1/intelligence/market-theses/latest`;
- `get_opportunities(instrument?, active_only?, limit?)` ->
  `GET /api/v1/opportunities`.

Use typed UUID and bounded integer inputs. Keep the existing opportunity page
limit below the downstream maximum. Return source JSON without changing Market
Thesis state, evidence, freshness, provenance or opportunity semantics.

Constraint: no prompts, resources, writes, TradePlan, Risk, Broker, cTrader or
AI Engine capability is registered.

### 4. Add offline MCP protocol coverage

Affected component: `mcp-adapter/tests/`.

Retain the HTTP client test and add tests that initialize the server through the
SDK, list tools, verify the exact tool set and invoke each tool against a mocked
Gateway. Add tests for invalid typed inputs, bounded opportunity limits,
authentication/HTTP failures, timeout handling and the absence of internal or
write paths.

Constraint: tests must not require a real Gateway, JWT, broker, cTrader, LLM or
remote MCP server.

### 5. Document local installation and OpenCode configuration

Affected component: `mcp-adapter/README.md` and the existing MCP investigation
if the tested SDK/OpenCode syntax differs materially from the current guidance.

Document isolated installation, environment variables, local launch, tool
mapping, secret handling, and the explicit non-support for remote MCP and
external cTrader MCP servers. Use the installed OpenCode version when verifying
the final configuration example.

Constraint: never include a real JWT or credential in repository documentation.

### 6. Reconcile the Story and ADR boundaries

Affected components: Story 0206 and ADR-088 only if implementation evidence
requires a factual clarification.

Keep ADR-088 accepted, with remote MCP and external MCP installation still
unauthorized. Do not change existing Gateway routes, service security or domain
ownership to make the adapter work.

## Files to Modify

- `mcp-adapter/pyproject.toml`: confirm the supported MCP SDK range and test
  dependency declarations.
- `mcp-adapter/app/client.py`: finalize bounded, controlled, secret-free Gateway
  HTTP behavior.
- `mcp-adapter/app/server.py`: finalize the exact three read-only tool schemas
  and SDK stdio entry point.
- `mcp-adapter/tests/test_client.py`: extend HTTP boundary and error coverage.
- `docs/investigations/mcp-first-agent-interaction-2026-11-05.md`: update only
  tested SDK/OpenCode details if required.

No changes are expected in Gateway, Market Data, Market Intelligence,
Trading Core, Broker Service or AI Engine.

## Files to Create

- `mcp-adapter/tests/test_server.py`: offline MCP initialization, tool listing,
  typed invocation and read-only path assertions.
- `mcp-adapter/README.md`: local setup, launch, configuration and safety guide.

No database migration, Java contract, Gateway route or external integration file
is required.

## Dependencies

Internal dependencies:

- public Gateway routes and JWT authentication;
- Market Data `GET /api/v1/markets`;
- Market Intelligence Market Thesis and opportunity reads.

External dependencies:

- official MCP Python SDK;
- `httpx`;
- pytest for offline validation.

No new service, database, message broker, provider SDK, cTrader dependency,
LLM dependency or remote MCP server is required.

Ordering dependencies:

1. Confirm the installed MCP SDK API and package range.
2. Complete client and tool boundary behavior.
3. Add protocol-level tests with mocked HTTP.
4. Validate the local launch and OpenCode configuration.
5. Run targeted quality validation and reconcile documentation.

## Test Plan

Create or update tests for:

- client bearer-header construction and token non-disclosure;
- local HTTP versus remote HTTPS URL policy;
- bounded request parameters;
- controlled 401, 403, 404, 5xx and timeout errors;
- MCP initialization over stdio or the SDK's supported in-process test
  transport;
- exact tool listing;
- typed UUID and bounded integer validation;
- mocked Gateway invocation for all three tools;
- no internal, provider or write URL invocation.

Validation commands:

```text
cd mcp-adapter
python3 -m pip install -e '.[test]'
python3 -m pytest
python3 -m compileall -q app tests
cd ..
git diff --check
```

Expected success conditions:

- all adapter tests pass offline;
- server imports without starting a blocking process;
- MCP tool listing contains exactly three approved tools;
- no external network request occurs during automated tests;
- compile and diff checks pass.

No Java or repository-wide Maven suite is required because no Java module or
public Java contract changes.

## Risks

### SDK API mismatch

Risk: the declared MCP SDK major version and import surface may differ from the
prototype.

Mitigation: pin the tested compatible range, verify imports in the isolated
environment and exercise tool listing through the SDK before implementation is
reported complete.

### Token exposure through diagnostics

Risk: HTTP exceptions or debug logging could include request headers.

Mitigation: do not log request headers, wrap errors into token-free messages and
add a test asserting the JWT is absent from tool/error output.

### Contract drift or semantic reinterpretation

Risk: the adapter could expose stale field assumptions or reinterpret Market
Thesis and Opportunity semantics.

Mitigation: test exact public paths and representative JSON fixtures; preserve
source JSON and document the authority boundaries.

### Oversized tool responses

Risk: evidence-rich responses may exceed a client context window.

Mitigation: retain bounded opportunity pagination and add a bounded response
policy before accepting the Story as complete. Any truncation must be explicit,
not silent.

### OpenCode configuration variance

Risk: generic MCP configuration examples may not match the installed OpenCode
version.

Mitigation: validate the final local registration against the installed client
and document the exact tested command/configuration.

No human clarification is required before implementation for the local scope.
Remote MCP authentication and external cTrader integration remain future
decisions and must not be resolved inside this Story.

## Validation Checklist

- [ ] `mcp-adapter/` installs in an isolated Python environment.
- [ ] The official MCP SDK version and import API are verified.
- [ ] The server runs over stdio and does not bind a network port.
- [ ] Exactly three read-only tools are listed.
- [ ] All Gateway calls use approved public GET paths.
- [ ] No `/internal/**`, provider, database, broker or AI Engine call exists.
- [ ] JWT is required, header-only, non-persisted and absent from outputs/logs.
- [ ] Remote Gateway URLs require HTTPS.
- [ ] Opportunity result size is bounded.
- [ ] Market Thesis evidence, state, freshness and provenance are preserved.
- [ ] No TradePlan, Risk, Execution, cTrader or external MCP behavior is added.
- [ ] Offline protocol and HTTP tests pass.
- [ ] OpenCode local configuration is tested against the installed version.
- [ ] Documentation is reconciled without adding secrets.
- [ ] `git diff --check` passes.

## Recommendation

Ready for implementation

The implementation strategy is sufficiently defined, ownership and contracts
are clear, and the remaining risks are bounded by offline tests and local-only
scope. This recommendation is technical and does not authorize implementation.

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
