# Repository Analysis — Story 0005

## Story Overview

- **Story ID:** `0005`
- **Title:** Account-Aware Active Scan Scope Resolution
- **Status:** Draft
- **Location:** `docs/architecture/stories/0005-account-aware-active-scan-scope-resolution/story.md`

## DevLog Context

### Retrieval Metadata

- `candidateCount`: `136`
- `selectedCount`: `60`
- `usedTokens`: `3103`
- `contextDigest`: `9ad1b917b3718d390bc573602f88ba2fff112cc682d9326afadf6af4a401e1ce`
- `truncated`: `true`

### Most Relevant DevLog Evidence

- `DEVLOG_CONFIRMED`: ADR-033 says Active Scanner is intention-driven and that deterministic market eligibility is distinct from contextual relevance.
- `DEVLOG_CONFIRMED`: ADR-033 says `ActiveScan` is a persistent orchestration concept, but this Story should not implement it yet.
- `DEVLOG_CONFIRMED`: ADR-033 says `AnalysisExecution` remains single-market and `PipelineRun` remains per-analysis provenance.
- `DEVLOG_CONFIRMED`: ADR-033 says raw `OpportunityScore` is not a globally calibrated cross-market ranking scale.
- `DEVLOG_CONFIRMED`: ADR-033 says AI must not override deterministic eligibility or risk authority.
- `DEVLOG_CONFIRMED`: DevLog surfaced Story 0002, Story 0004, ADR-020, ADR-023, ADR-025, ADR-026, ADR-028, ADR-030, and ADR-032 as the relevant lineage.

## Current Repository State

### Market Intelligence

- `REPOSITORY_CONFIRMED`: the public market-intelligence entry point is `POST /api/v1/intelligence/analyses`.
- `REPOSITORY_CONFIRMED`: the current request model only accepts one `marketId`, one `mode`, and one `objective`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecutionService` creates a single `AnalysisExecution` and dispatches it asynchronously.
- `REPOSITORY_CONFIRMED`: `ActiveAnalysisStrategy` and `PassiveAnalysisStrategy` already exist.
- `REPOSITORY_CONFIRMED`: `CapabilityAnalysisCoordinator` assembles context and runs deterministic capabilities.
- `REPOSITORY_CONFIRMED`: `ObservationBuilder` creates immutable observations from completed capability output.
- `REPOSITORY_CONFIRMED`: `OpportunityEngine` creates immutable opportunities from observations.
- `REPOSITORY_CONFIRMED`: `ProductionIntelligencePipeline` records a per-analysis pipeline run.

### Trading Core

- `REPOSITORY_CONFIRMED`: `AccountController` exposes owned trading accounts by authenticated user.
- `REPOSITORY_CONFIRMED`: `BrokerAccountController` and `InternalBrokerAccountController` expose owned broker-account data by authenticated user.
- `REPOSITORY_CONFIRMED`: `AccountRepository` supports lookup by owner / username.
- `REPOSITORY_CONFIRMED`: `BrokerAccountRepository` supports lookup by owner id.
- `REPOSITORY_CONFIRMED`: `TradePlanRiskEvaluationService` owns deterministic risk validation and remains authoritative.

### Market Data

- `REPOSITORY_CONFIRMED`: `MarketController` exposes the market catalog and individual markets.
- `REPOSITORY_CONFIRMED`: `MarketResponse` includes `MarketState`.
- `REPOSITORY_CONFIRMED`: `MarketState.tradable` is available for deterministic hard eligibility.
- `REPOSITORY_CONFIRMED`: `MarketController` also exposes OHLC history and subscriptions.

## Current Problem

The repository can analyze one market, but it cannot yet resolve a deterministic effective scan scope for a selected account context before deep analysis.

That means there is currently no explicit step for:

- candidate universe resolution;
- account-aware hard eligibility;
- deterministic exclusion diagnostics;
- effective scan scope construction.

## Ownership Matrix

| Context information | Authority | Current repository source | Story 0005 role |
|---|---|---|---|
| User identity | `trading-core` security | `UserDto` from JWT principal | Validate ownership only |
| Trading account | `trading-core` | `AccountController`, `AccountRepository` | Authoritative account lookup |
| Broker account | `trading-core` | `BrokerAccountController`, `BrokerAccountRepository` | Authoritative broker lookup |
| Market catalog | `market-data` | `MarketController.GET /api/v1/markets` | Candidate universe source |
| Tradability | `market-data` | `MarketState.tradable` | Hard eligibility rule |
| Risk rules | `risk-domain` / `trading-core` | `TradePlanRiskEvaluationService` | Must remain downstream |

## Reuse Opportunities

| Component | Reuse status | Notes |
|---|---|---|
| `MarketDataClient` | `REUSE_AS_IS` | Already provides market lookup and OHLC history |
| `MarketController` | `REUSE_AS_IS` | Existing market catalog source |
| `AccountController` / `AccountRepository` | `REUSE_WITH_SMALL_EXTENSION` | Use for ownership validation and selected account lookup |
| `BrokerAccountController` / `BrokerAccountRepository` | `REUSE_WITH_SMALL_EXTENSION` | Use for broker-account ownership and status validation |
| `AnalysisExecution` | `DO_NOT_REUSE_FOR_SCAN_AGGREGATION` | Remains single-market |
| `PipelineRun` | `DO_NOT_REUSE_FOR_SCAN_AGGREGATION` | Per-analysis provenance only |
| `ObservationBuilder` | `REUSE_AS_IS` | Downstream only |
| `OpportunityEngine` | `REUSE_AS_IS` | Downstream only |
| `TradePlanningEngine` | `REUSE_AS_IS` | Downstream only |
| `Risk Domain` | `DO_NOT_REUSE_FOR_SCAN_AGGREGATION` | Authority remains downstream |

## Missing Contracts

- no account-aware scope-resolution service exists in `market-intelligence`;
- no explicit deterministic eligibility / effective-scope model exists yet;
- no read-only trading-core client exists in `market-intelligence` for account/broker context;
- no auth-propagating Feign configuration exists in `market-intelligence` today;
- no scope-resolution API exists yet.

## Security Considerations

- Story 0005 must never allow a user to resolve scope against another user's account.
- Ownership checks must remain authoritative in `trading-core`.
- If `market-intelligence` calls `trading-core`, the authenticated principal must be propagated, not recreated.
- No broker credentials should cross the boundary.

## Domain Boundaries

### Must Not Change

- `AnalysisExecution` cardinality.
- `PipelineRun` semantics.
- Risk Domain determinism.
- Passive Scanner independence from the selected account.

### Must Preserve

- single-market analysis semantics;
- deterministic observations and opportunities;
- downstream trade planning and risk validation;
- AI as a non-authority layer.

## Eligibility Boundary

Story 0005 should only include deterministic hard eligibility that already has an authoritative source:

- market exists;
- market is tradable;
- selected account is owned by the authenticated user;
- selected broker account is owned by the authenticated user and usable if that fact is already available.

Everything else remains downstream or deferred.

## Persistence Impact

No persistence is required for scope resolution itself.

The output should be derived on demand and remain transient unless future `ActiveScan` persistence requires otherwise.

## API Impact

The slice is easiest to validate through a small read-only scope-resolution endpoint or equivalent application entry point.

The gateway already routes `/api/v1/intelligence/**`, so no gateway change is expected.

## AI Impact

No AI is required.

This Story is about deterministic eligibility and effective scope only.

## Alternatives Considered

| Alternative | Verdict | Reason |
|---|---|---|
| Implement `ActiveScan` now | Rejected | Too large; would skip the boundary this Story is meant to validate |
| Resolve scope using market data only | Rejected | Not account-aware |
| Move scope resolution into `trading-core` | Rejected | Orchestration belongs with market-intelligence |
| Introduce a generic policy framework | Rejected | Too much abstraction for the current need |
| Add AI relevance now | Rejected | Not required and would blur deterministic authority |

