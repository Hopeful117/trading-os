# Implementation Plan - Story 0207

## Scope Decision

Implement the read-only and validation portions of Story 0207 without changing
ADR-088 or enabling a controlled analytical command.

The plan will not implement `start_market_scan`, direct analysis submission,
analysis cancellation or any AI Engine operation. These operations require a
separate authorization and architecture decision because the accepted MCP
boundary is read-only and the current scan contract is account-scoped.

## Objectives

1. Make canonical market discovery useful and explicit for an MCP client.
2. Expose the existing authenticated active-scan projection as a bounded,
   read-only MCP capability, if its ownership and response semantics remain
   suitable.
3. Preserve the existing Market Thesis and opportunity capabilities unchanged in
   authority and semantics.
4. Prove JWT, Gateway and error behavior offline without using real credentials.
5. Document a reproducible research workflow and clearly separate deterministic
   facts, AI interpretation, missingness and research conclusions.

## Planned Changes

### 1. Refine Market Discovery

Affected files:

- `mcp-adapter/app/server.py`;
- `mcp-adapter/tests/test_server.py`;
- `mcp-adapter/README.md`.

Keep `list_markets` mapped to `GET /api/v1/markets` and preserve the complete
source JSON. Add schema/documentation fixtures asserting that market identity
and provenance remain visible, including `marketId`, `provider`, `symbol`,
`productType`, `marketState` and contract metadata.

Do not add symbol inference or a second market catalogue. Do not add
`resolve_market` unless implementation evidence shows that the existing list
contract cannot support unambiguous selection; in that case, use the existing
public `GET /api/v1/markets/{marketId}` contract only.

### 2. Add Read-Only Active-Scan Projection

Affected files:

- `mcp-adapter/app/server.py`;
- `mcp-adapter/tests/test_server.py`;
- `mcp-adapter/README.md`.

Add a narrowly scoped typed tool, tentatively `get_active_scan(scan_id)`, mapped
to `GET /api/v1/intelligence/scans/{scanId}`. The tool must:

- accept a UUID only;
- issue only a Gateway GET;
- preserve scan status, progress, per-market analysis status, result quality,
  diagnostics, execution IDs and opportunities;
- preserve empty, partial, failed, cancelled and incomplete states;
- rely on Gateway and Market Intelligence ownership checks;
- expose no account mutation or execution command.

Do not expose the account ID as a selector or allow access to another actor's
scan. The Gateway response remains the source of truth for authorization.

### 3. Preserve Existing Intelligence Reads

Keep `get_market_thesis` and `get_opportunities` unchanged except for tests and
documentation needed to validate the research workflow. No semantic projection,
score conversion, evidence compression or state promotion is allowed.

Direct `GET /api/v1/intelligence/analyses/{executionId}` and result endpoints
remain unexposed because downstream security currently denies the public
analysis prefix. Internal endpoints remain forbidden.

### 4. Harden Offline Authentication Coverage

Affected files:

- `mcp-adapter/app/client.py` only if a minimal behavior gap is demonstrated;
- `mcp-adapter/tests/test_client.py`.

Add mocked Gateway coverage for:

- valid bearer propagation;
- missing JWT before any request;
- HTTP 401 for invalid or expired JWT;
- HTTP 403 for ownership/authorization denial;
- Gateway timeout and unavailability;
- token absence from errors and returned tool content;
- remote HTTP rejection and local HTTP allowance.

Do not add JWT parsing, refresh, browser extraction, persistence or credential
storage to the adapter. Expired versus invalid tokens are represented by the
Gateway status contract, not inferred locally.

### 5. Validate Research Workflow as a Client-Side Procedure

Affected files:

- `mcp-adapter/README.md`;
- `docs/investigations/mcp-first-agent-interaction-2026-11-05.md` only if its
  current capability mapping needs factual updates;
- Story 0207 implementation report.

Document the bounded read-only sequence:

1. call `list_markets`;
2. select canonical BTC and ETH markets by explicit provider/product identity;
3. retrieve available Market Thesis values;
4. retrieve bounded opportunities;
5. retrieve an already-created active-scan projection when a scan ID is supplied
   by an authorized workflow;
6. compare evidence while preserving contradictions, missingness and freshness;
7. produce a synthesis that labels deterministic facts, AI interpretation,
   assumptions and research conclusions.

The adapter does not synthesize authoritative market truth. Any narrative
comparison remains a client/research projection and must not claim profitability,
approval, safety or execution readiness.

### 6. Document Deferred Controlled Commands

Affected files:

- Story 0207 implementation report;
- Story 0207 code review report.

Record that `start_market_scan`, direct analysis lifecycle commands and AI
interpretation commands are deferred. A future ADR-088 amendment must define
command authorization, account selection, idempotency policy, rate/concurrency
limits, auditability and user confirmation before implementation.

No ADR amendment is created by this plan because no controlled command is being
enabled.

## Files to Modify

- `mcp-adapter/app/server.py`;
- `mcp-adapter/tests/test_server.py`;
- `mcp-adapter/tests/test_client.py`;
- `mcp-adapter/README.md`;
- `docs/investigations/mcp-first-agent-interaction-2026-11-05.md` only when
  required by factual capability changes;
- Story 0207 implementation and review artifacts.

No Gateway, Market Data, Market Intelligence, AI Engine, Risk, Broker or Trading
Core source code changes are planned.

## Test Plan

Run offline tests using mocked Gateway responses:

- MCP initialization and exact tool listing;
- typed UUID validation for active-scan lookup;
- canonical market fields and provider/product distinction;
- active-scan path mapping and faithful response preservation;
- complete, partial, failed, cancelled, empty and missing scan projections;
- Market Thesis complete, contradictory, stale and missing evidence fixtures;
- empty opportunities and bounded pagination;
- valid, missing, invalid and expired-token response handling;
- 401, 403, 404, 5xx, timeout, network and oversized-response handling;
- absence of internal, provider, write and execution paths;
- stdio subprocess startup and tool discovery.

Validation commands:

```text
cd mcp-adapter
.venv/bin/pytest
.venv/bin/python -m compileall -q app tests
cd ..
git diff --check
```

No live Gateway, broker, Kraken, cTrader, LLM or external MCP call is part of
automated validation.

## Live Validation Boundary

Live validation is conditional and has not been authorized or executed. If a
human provides an explicitly authorized local Gateway, short-lived JWT and test
account, the validation may cover discovery and authenticated reads only. A scan
must not be started under this plan. The final report must distinguish executed,
blocked and unavailable checks without claiming an end-to-end scan.

## Risks

- The active-scan projection may expose account-scoped data and must remain
  protected by the Gateway actor ownership check.
- The direct analysis endpoints remain blocked by downstream security; adding a
  bypass would violate the Story and ADR boundaries.
- Market catalogue entries may contain multiple provider/product variants for a
  symbol; tests must require explicit identity rather than symbol-only matching.
- Research synthesis can overstate opportunities; documentation and fixtures
  must preserve the distinction between evidence, interpretation and decision.

## Expected Result

The local MCP adapter supports a truthful read-only research workflow over
existing public Gateway contracts. It can inspect markets, deterministic thesis,
opportunities and an authorized existing scan projection, but cannot start a
scan, submit analysis, invoke AI, evaluate risk or execute a trade.

## Recommendation

Ready for implementation after explicit human approval of this plan.
