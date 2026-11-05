# Story 0207 - MCP End-to-End Validation & Research Workflow

## Mission

Implement and validate the first operational MCP-driven market research workflow in Trading OS.

The goal is to enable an external MCP client such as OpenCode to discover markets, trigger an authorized market scan, inspect deterministic market intelligence, and produce an evidence-based research synthesis.

This Story builds upon Stories 0204, 0205 and 0206, and ADR-088. Do not redesign the existing MCP architecture. Validate and incrementally extend it.

## Repository Investigation

Before implementation:

- inspect Git status, branch, HEAD and uncommitted changes;
- read `AGENTS.md`;
- read ADR-020, ADR-021, ADR-034, ADR-076 and ADR-088;
- inspect Stories 0204, 0205 and 0206, including engineering reports;
- inspect `mcp-adapter/`, Gateway authentication and routing, Market Data discovery, Market Intelligence scan and analysis lifecycle contracts, and AI Engine boundaries;
- consult DevLog when available and verify its context against the repository;
- document actual endpoints and contracts rather than assuming endpoints exist.

## Architecture Constraints

Preserve the ADR-088 boundary:

```text
OpenCode / MCP Client -> MCP stdio adapter -> Existing Trading OS Gateway APIs
```

The adapter remains independent of OpenCode, uses the Gateway, reuses application contracts, and never accesses Java repositories, internal endpoints, broker providers, or duplicated market-analysis logic. Market Intelligence remains responsible for analysis and Market Thesis; AI Engine remains responsible for AI interpretation; Trading Core and Risk Domain remain authoritative for account and risk decisions.

## Authentication Validation

Validate the existing configuration:

```text
TRADING_OS_GATEWAY_URL
TRADING_OS_JWT
TRADING_OS_HTTP_TIMEOUT_SECONDS
TRADING_OS_MAX_RESPONSE_BYTES
```

Investigate and, when authorized environment and credentials exist, validate JWT propagation, Gateway authentication, Market Intelligence authorization and ownership boundaries, expired and invalid JWTs, missing JWTs, Gateway unavailability and token non-disclosure.

Never store JWTs in the repository, OpenCode configuration or logs. Do not extract credentials from a browser or introduce refresh-token persistence. If no authenticated local session is available, implement offline contract tests and document the exact manual steps. Never fabricate live validation.

## Market Discovery

Make discovery usable by an agent while reusing existing public Gateway contracts. Resolve canonical market ID, instrument identifier, provider, trading symbol, market state and supported market type. Prefer refining `list_markets`; add a narrowly scoped `get_market` or `resolve_market` only when an existing public contract supports it. Do not invent identifiers or mappings. Resolve ambiguous symbols explicitly and preserve provider provenance, including Kraken Spot versus Kraken Futures where applicable.

## Controlled Market Scan

Investigate the existing active-scan API, including endpoint, method, authentication, request DTO, market selection, execution ID and states, idempotency, concurrency, timeouts, persistence and result retrieval.

If an authenticated and controlled public contract exists, a capability such as `start_market_scan(...)` may be proposed or implemented using that contract. It must have explicit market selection, bounded inputs, validation, authorization, duplicate-submission protection, explicit errors and no automatic repeated scans. Do not call internal endpoints, create a second scanner or scheduler, or allow unbounded scans.

A scan is a controlled analytical command, not a read-only operation. Do not silently extend ADR-088. If an authorization policy or ADR amendment is required, implement only approved read-only portions and present the decision before enabling a scan tool.

## Analysis Lifecycle

Investigate existing execution status and result retrieval contracts. Expose only the minimum necessary capabilities to retrieve status, completion, results and failed, cancelled, expired or incomplete states while preserving execution identifiers and diagnostics. Avoid blocking MCP calls and keep polling bounded and client-controlled. Do not create another scheduler or persistence model.

## Market Intelligence Outputs

Ensure completed analysis outputs can be retrieved through existing public Gateway contracts. Preserve market identity, analysis ID, timeframes, supporting, contradicting and missing evidence, freshness, provenance, rule versions, thesis state and invalidation conditions.

Never convert `CANDIDATE` into a recommendation, opportunity ranking into probability, missing evidence into neutral evidence, or AI interpretation into deterministic market truth. Return explicit valid empty results when no opportunity exists.

## Research Workflow

Validate this interaction:

> Analyse BTC et ETH, compare les configurations actuelles et explique quels marchés méritent une surveillance.

The workflow must discover markets, resolve canonical identifiers, request authorized scans only when approved, retrieve execution status and results, retrieve Market Thesis and opportunities, compare evidence, identify contradictions and missing information, and produce a structured synthesis.

The synthesis must distinguish Trading OS deterministic facts, AI interpretation, missing or stale data, unverified assumptions and research conclusions. It must not claim that a trade is profitable, safe or approved merely because an opportunity exists. No order execution or autonomous trading is allowed.

## AI Engine Boundary

Do not reimplement the AI Engine client or Market Thesis interpreter. Investigate whether interpretation results are available through a public Gateway contract. Add a read-only capability only if trivial and security-neutral; otherwise defer it. Deterministic market research remains the first priority.

## Security and Scope

Read operations may include market discovery, Market Thesis, opportunities, analysis status and result retrieval. Controlled analytical commands may include starting a market scan or AI interpretation only after their authorization boundary is approved. Trade Plans, risk changes, orders, cancellations, broker credentials and live-position management remain out of scope.

Do not expose broker execution tools, connect external cTrader MCP servers, modify Risk/T0/T1/execution workflows, bypass Gateway authentication, or weaken HTTPS enforcement. Protect JWTs and sensitive response fields, avoid logging headers or sensitive payloads, and preserve ownership checks.

## Output Management

Investigate response sizes and retain bounded pagination, bounded response size, explicit oversized-response errors, complete evidence, contradictions, freshness and provenance. Never silently truncate or conflate unavailable and omitted data. Do not add a complex context-compression framework; non-authoritative summarization must remain outside the authoritative adapter.

## Testing

Extend the MCP tests with:

- protocol initialization, tool discovery, typed parameters, Gateway mappings and valid responses;
- valid, missing, invalid and expired JWT behavior, unauthorized/forbidden responses and token non-disclosure;
- known, unknown, ambiguous and empty market discovery, including multiple providers;
- scan submission, duplicate request, invalid market, running, completed, failed, timeout, cancelled and missing-result states when scan capability is authorized;
- complete, missing, contradictory and stale research evidence, empty opportunities, oversized responses and Gateway unavailability.

Use mocked Gateway responses for automated validation. Do not require Kraken, cTrader, broker or LLM access.

## Live Validation

Only attempt local end-to-end validation when the required environment and credentials are explicitly available and authorized:

1. start the required Trading OS services;
2. verify Gateway health;
3. start OpenCode with the adapter;
4. verify tool discovery;
5. invoke market discovery and authenticated intelligence reads;
6. trigger a bounded scan only if separately authorized;
7. retrieve status and results;
8. compare returned information with Trading OS.

If unavailable, report the tests as not executed and provide reproducible instructions. Never fabricate live evidence.

## Documentation

Update the MCP adapter README, OpenCode integration guide, capability mapping, security documentation, the ADR-088 amendment proposal if controlled scan operations are introduced, the Implementation Report and the Code Review Report. Document reproduction steps, limitations and remaining manual decisions.

## Acceptance Criteria

- The adapter remains independent of OpenCode and reuses existing Gateway contracts.
- Market discovery is usable and preserves provider and source semantics.
- Authentication behavior is tested without secret disclosure.
- Market Thesis and opportunity retrieval are reliable and semantically faithful.
- The end-to-end research workflow is documented and tested.
- Scan initiation is implemented only after its authorization boundary is approved.
- Errors, missing data and live-validation limitations are explicit.
- No sensitive trading operation, duplicated business logic or autonomous execution is exposed.
- Existing Risk and Execution guarantees remain unchanged.
- All relevant offline tests pass.

## Delivery Constraints

- Do not commit, push or merge without authorization.
- Preserve unrelated local changes.
- Avoid unnecessary dependencies and chat wrappers.
- Do not introduce LangGraph or another agent framework.
- Do not modify AI Engine unless strictly necessary.
- Do not build new broker integrations.
- Do not enable autonomous execution.

## Expected Final Report

1. Repository state and HEAD.
2. Existing contracts reused.
3. MCP capabilities added or improved.
4. Authentication validation results.
5. Research workflow validation results.
6. Automated tests executed.
7. Live tests executed or explicitly not executed.
8. Security and architectural decisions requiring approval.
9. Known limitations.
10. Recommended next Engineering Story.

## Status

Implementation complete - human delivery review required. Controlled scan
initiation and direct analysis lifecycle tools remain deferred pending a future
ADR-088 authorization decision.
