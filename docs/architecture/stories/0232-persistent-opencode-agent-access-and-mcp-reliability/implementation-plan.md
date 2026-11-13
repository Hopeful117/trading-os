# Implementation Plan - Story 0232

## Status

Completed - waiting for human approval before implementation

## Objective

Make an already authorized OpenCode agent recover its MCP session across
OpenCode/bridge restarts and short-lived JWT expiry, while returning actionable
sanitized diagnostics for bounded read operations. Preserve all existing
agent-grant, LIVE-account, Risk and human-execution boundaries.

## Constraints

- Reuse the existing opaque credential, `AgentIdentity`, `AgentCredential`,
  `AgentGrant` and agent JWT contracts.
- No new identity system, authorization server, service or PAPER runtime.
- Do not replay POST requests automatically.
- Do not change FTMO Free cTrader from `ExecutionMode.LIVE`.
- Do not alter `executionAllowed`, `riskEvaluationAllowed`, T0/T1 Risk or
  human execution authorization semantics.
- Preserve all unrelated uncommitted work; reconcile overlapping diffs before
  editing.

## Work Packages

### 1. MCP credential and session recovery

Files:

- `mcp-adapter/app/client.py`
- `mcp-adapter/app/bridge.py`
- `mcp-adapter/tests/test_client.py`
- `mcp-adapter/tests/test_bridge.py`

Changes:

- Load the owner-only opaque credential during bridge startup and establish
  connected state only after a successful token exchange.
- Keep credential persistence atomic and fail closed on invalid permissions,
  malformed content or rejected exchange.
- Coordinate token exchange and invalidation with a lock so concurrent GET
  requests perform one refresh rather than a refresh storm.
- Preserve one GET retry after `401`; never retry POST.
- Distinguish token expiry, invalid/revoked credential and Gateway
  unavailability without exposing credential material.
- Generate and propagate a bounded `X-Correlation-ID` for each request when
  none is supplied.

### 2. Structured MCP diagnostics

Files:

- `mcp-adapter/app/client.py`
- `mcp-adapter/app/server.py`
- `mcp-adapter/tests/test_client.py`
- `mcp-adapter/tests/test_server.py`

Changes:

- Introduce a sanitized structured client error carrying stable code, safe
  message, HTTP status, correlation ID, retryability and next action.
- Parse safe Problem Detail properties from Gateway/Trading Core while
  discarding arbitrary response fields and stack traces.
- Preserve categories for authentication, authorization, account access,
  incomplete facts, market data, upstream, invalid request and internal
  failures.
- Ensure the MCP tool surface serializes the structured error rather than
  exposing only a generic exception string.
- Keep response-size protection active and classify oversized responses as a
  bounded client error.

### 3. Market read reliability

Files, subject to current diff reconciliation:

- `mcp-adapter/app/server.py`
- `market-data/src/main/java/com/hope/trading/market_data/controller/MarketController.java`
- `market-data/src/test/java/com/hope/trading/market_data/controller/MarketControllerTest.java`
- `mcp-adapter/tests/test_server.py`

Changes:

- Preserve the existing optional `instrument` filter and ensure it is forwarded
  from the active MCP source.
- Keep bounded filtered discovery as the normal MCP path; do not raise the
  global response cap as the fix.
- Add a regression proving EUR/USD filtering is forwarded and that an
  unbounded oversized response remains rejected.
- Do not change market identity or catalog semantics.

### 4. Authorized positions and readiness separation

Files:

- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentBusinessController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountReadOnlyStatusService.java`
- relevant agent/business exception handler and correlation configuration
- `trading-core/src/test/java/com/hope/trading/trading_core/agent/api/AgentBusinessControllerTest.java`
- relevant Gateway/Feign tests

Changes:

- Preserve exact owner and `positions:read` grant checks before any broker read.
- Classify downstream snapshot timeout, unavailable response, malformed data
  and incomplete facts separately where the existing domain contract supports
  it; do not fabricate positions or account facts.
- Return a stable safe error code and correlation ID for a failed provider
  snapshot/mapping path.
- Keep `CONNECTED_BUT_FACTS_INCOMPLETE`, `executionAllowed=false` and
  `riskEvaluationAllowed=false` authoritative.
- Do not block an unrelated authorized read solely because execution/risk facts
  are incomplete unless the contract explicitly requires those facts.
- Add tests for empty positions, incomplete facts, upstream failure, malformed
  response, missing grant and cross-owner access.

### 5. UI reconnect behavior

Files:

- `trading-os-web/src/app/core/services/agent.service.ts`
- `trading-os-web/src/app/features/agents/pages/agents/agents.ts`
- `trading-os-web/src/app/features/agents/pages/agents/agents.html`
- corresponding Angular specs

Changes:

- Stop making ordinary reconnect depend on issuing a new credential when the
  existing active agent/bridge session is recoverable.
- Retain explicit actions for enrollment, credential rotation/recovery, status,
  grant management and revocation.
- Keep initial enrollment human-controlled and display safe recovery guidance
  only when the credential is missing, invalid, revoked or the bridge is
  unavailable.
- Do not expose credentials in UI state, logs or error messages.

### 6. Documentation and operational evidence

Files:

- ADR-099 and Story 0232 artifacts already created.
- Story implementation report and runtime validation report after code/tests.

Changes:

- Mark ADR-099 accepted only after human architectural validation; otherwise
  retain proposed status.
- Record exact root causes, test evidence, runtime statuses and limitations.
- Document that no broker mutation, synchronization, approval or execution was
  performed.

## Test Plan

1. MCP Adapter unit tests for persisted credential loading, permission checks,
   exchange caching, expiry, concurrent refresh, GET retry, POST no-retry,
   structured error parsing and secret sanitization.
2. MCP bridge tests for startup recovery, invalid credential, revoked
   credential, persistence failure and pairing compatibility.
3. Trading Core tests for exact grants, owner isolation, position-read mapping,
   account facts incompleteness and sanitized error responses.
4. Gateway tests for agent routes and correlation propagation.
5. Angular service/page tests for enrollment, recovery, revocation and no
   mandatory reconnect approval for an existing ACTIVE agent.
6. Run targeted module tests, `git diff --check`, and repository quality checks
   applicable to changed modules.

## Runtime Validation

Use only the active OpenCode MCP integration against Gateway `17080` and the
existing broker account `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`:

- verify agent token exchange and restart recovery;
- call filtered `list_markets` for EUR/USD;
- call account listing, account details, status and positions;
- verify LIVE/provider/account identity and readiness restrictions;
- verify structured failures include safe status/code/correlation data;
- inspect opportunities only if the read grant and endpoint are available;
- stop before scan, Trade Plan, risk evaluation, approval or execution unless
  the approved Story explicitly permits a read-only risk check.

No credential value, JWT, broker secret or provider payload may be recorded.

## Rollback

Revert only the Story code/configuration changes after review. Do not delete or
rotate the existing credential, revoke grants, alter the FTMO broker account,
apply migrations to shared runtimes or create a synthetic environment as a
rollback mechanism.

## Expected Deliverables

- Implemented code and focused regression tests.
- Updated documentation and implementation report.
- Structured MCP error examples with secrets removed.
- Read-only runtime evidence and final verdict.
- No commit, push, PR or broker mutation.
