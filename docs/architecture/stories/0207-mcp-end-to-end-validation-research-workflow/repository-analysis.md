# Repository Analysis - Story 0207

## Story Understanding

Story 0207 extends the local MCP anti-corruption adapter from Story 0206 into a
truthful market-research workflow. The adapter must discover canonical markets,
read deterministic intelligence, and validate authentication and research
provenance through the public Gateway. Starting an active scan is a controlled
analytical command, not a read-only operation, and is not authorized by ADR-088
without an explicit boundary decision.

## Repository State

- Branch: `main`
- HEAD: `491e9b6d` (`Merge pull request #250 from Hopeful117/story/0206-mcp-first-read-only-agent-interaction`)
- Worktree: clean except for the new untracked Story 0207 directory.
- Story ID `0207` was unused and is now represented by its canonical `story.md`.
- No application code has been modified for Story 0207.

## Relevant Architectural Context

### ADR-020 and ADR-021

Market Intelligence owns context assembly, analysis orchestration, execution
state and result consolidation. `AnalysisExecution` is durable and asynchronous;
its lifecycle includes `REQUESTED`, `ACCEPTED`, `CONTEXT_BUILDING`, `RUNNING`,
`PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`, `CANCELLED` and `EXPIRED`.
Idempotency, bounded retries, freshness and explicit partial quality are part of
the existing contract. The MCP adapter must not duplicate this orchestration or
the AI Engine boundary.

### ADR-034 and ADR-076

Observations, StrategyMatch, TradingOpportunity and Market Thesis retain
distinct meanings. A Market Thesis is deterministic, market-scoped,
strategy-neutral and preserves supporting, contradicting, stale, unavailable
and missing evidence. `CANDIDATE` is not a recommendation, Risk approval or
execution authorization. Opportunity scores are not calibrated probabilities.

### ADR-088

The current accepted MCP boundary is local stdio over the public Gateway. The
adapter must not call `/internal/**`, service discovery, repositories, providers,
AI Engine or broker services. It exposes only the three read-only tools from
Story 0206. Remote MCP and external cTrader MCP remain unauthorized.

## Existing MCP Adapter

`mcp-adapter/app/server.py` currently exposes exactly:

- `list_markets` -> `GET /api/v1/markets`;
- `get_market_thesis` -> `GET /api/v1/intelligence/market-theses/latest`;
- `get_opportunities` -> `GET /api/v1/opportunities`.

`app/client.py` sends the configured JWT only as an Authorization bearer header,
requires a JWT, enforces HTTPS for non-local Gateway URLs, bounds response size,
validates JSON and sanitizes HTTP, timeout and network errors. Existing tests
cover MCP initialization, exact tool listing, typed arguments, public path
mappings, bearer propagation, error translation and response limits.

The adapter currently returns source JSON and has no scan, polling, write,
provider or AI Engine capability.

## Confirmed Public Contracts

### Market Discovery

Gateway routes `/api/v1/markets/**` to Market Data. `MarketController` exposes:

- `GET /api/v1/markets`: public canonical catalogue;
- `GET /api/v1/markets/{marketId}`: public canonical market lookup;
- provider, symbol, base/quote assets, product type, constraints and market state
  are present in `MarketResponse`.

The persisted identity is unique by provider, symbol and product type. The model
also carries provider-specific symbol data internally. `MarketProvider` includes
Kraken and cTrader among others; `InstrumentProductType` distinguishes SPOT,
LINEAR_PERPETUAL and CFD. The adapter must preserve these fields and must not
resolve an ambiguous symbol by inference.

### Deterministic Intelligence

Gateway routes `/api/v1/intelligence/**` to Market Intelligence and
`/api/v1/opportunities/**` to Market Intelligence. The relevant authenticated
reads are:

- `GET /api/v1/intelligence/market-theses/latest?marketId=...&instrument=...&direction=...`;
- `GET /api/v1/opportunities` with bounded page, instrument, activeOnly, sort and
  direction parameters;
- `GET /api/v1/intelligence/scans/{scanId}` returning an active-scan projection
  with scan status, progress, per-market analysis status/result quality,
  diagnostics and opportunities.

The current Market Thesis tool already maps the first contract. The opportunity
tool applies an MCP limit of 50, below the downstream maximum of 200.

### Active Scan

`POST /api/v1/intelligence/scans` is an authenticated public Gateway route. It
requires an `Idempotency-Key` header and a body containing:

- `accountId`;
- optional bounded `objective`;
- optional `requestedMarketIds`;
- optional `scopeMode` (`SELECTED` or `ALL_ELIGIBLE`).

The service resolves an account-owned scope, persists the scan, protects duplicate
requests with an actor-scoped idempotency key and returns `202 Accepted` with a
scan resource. The scan projection is retrieved with
`GET /api/v1/intelligence/scans/{scanId}` and performs ownership checks through
the authenticated actor. This is not a generic public market scan: it is an
account-scoped operation and requires a real account identifier.

### Analysis Execution

The controller defines:

- `POST /api/v1/intelligence/analyses`;
- `GET /api/v1/intelligence/analyses/{executionId}`;
- `GET /api/v1/intelligence/analyses/{executionId}/result`;
- `POST /api/v1/intelligence/analyses/{executionId}/cancel`.

However, `MiSecurityConfiguration` explicitly denies
`/api/v1/intelligence/analyses/**`. The public Gateway routes the prefix, but the
downstream security policy prevents these reads and commands from being usable
through the public contract. The internal execution controller under
`/internal/v1/intelligence/analyses` is service-authorized and must not be used
by MCP. This is a material contract/security contradiction requiring human
decision before exposing execution-status or result tools.

The active-scan projection may provide sufficient status and result information
for the research workflow without exposing direct analysis execution endpoints,
but this must be verified against representative response fixtures and the
desired ownership semantics.

## Authentication Findings

Market Intelligence requires an authenticated user JWT for `/api/v1/intelligence/**`
and `/api/v1/opportunities/**`; Market Data permits `GET /api/v1/markets/**`.
The Gateway remains the authentication boundary and the adapter must continue to
pass the user JWT without persistence, refresh, browser extraction or logging.

Offline tests can cover missing JWT, bearer propagation, 401, 403, expired/invalid
token responses and token non-disclosure. A real authenticated Gateway test needs
an explicitly authorized short-lived JWT, a known local environment and test
account/data. No such credentials were inspected or used during this analysis.

## AI Engine Boundary

Story 0205 provides an AI Engine behind `AiEnginePort` and keeps it disabled by
default. The AI result is advisory and must not mutate canonical Market Thesis,
Risk, Trade Plan or Execution state. No public Gateway contract for retrieving AI
interpretation was identified in the inspected controllers. AI retrieval should
therefore be deferred unless a public, read-only and security-neutral contract is
confirmed.

## Proposed Implementation Scope

Subject to human approval and resolution of the security contradiction:

1. Strengthen `list_markets` output/fixtures so agents can select canonical
   market IDs using provider, symbol, product type and state.
2. Add only read-only MCP capabilities backed by confirmed public Gateway reads,
   likely active-scan status/projection if its response is sufficient.
3. Add offline mocked-Gateway tests for authentication, market ambiguity,
   provider identity, missingness, contradiction, freshness, empty results,
   oversized responses and Gateway failures.
4. Document the research workflow and explicitly report live validation as not
   executed unless an authorized environment is provided.
5. Do not add `start_market_scan` until an ADR-088 amendment or explicit
   authorization boundary approves controlled analytical commands.

No new scheduler, persistence model, Java business logic, provider integration,
AI Engine client, risk behavior or execution behavior is justified by the current
evidence.

## Risks and Required Decisions

1. **Controlled-command boundary:** enabling `start_market_scan` would amend the
   accepted read-only ADR-088 boundary and needs explicit human architecture and
   security approval.
2. **Account scope:** the existing scan contract requires `accountId`, so a pure
   market-research request for BTC and ETH needs an explicit account/context
   policy; the adapter must not invent or silently select an account.
3. **Execution read contract:** direct analysis status/result routes are denied
   by downstream security even though controllers exist. Human guidance is
   required on whether to reuse scan projection or change the public contract.
4. **Live authentication:** no live JWT, local Gateway session or test account
   was available or used; live E2E evidence must not be claimed.

## DevLog and Vault

The DevLog context request was attempted but the MCP service closed the
connection before returning context. The current repository remains authoritative
for this analysis. The Obsidian vault was not consulted because no vault context
was required to establish the current code and contract boundaries.

## Recommendation

**Repository Analysis complete - approval required.**

The read-only research portions are implementable against existing public
contracts. Controlled scan initiation and direct analysis lifecycle tools must
remain deferred until the security/architecture decisions above are explicitly
resolved. Human approval of this Repository Analysis is required before an
Implementation Plan is produced.
