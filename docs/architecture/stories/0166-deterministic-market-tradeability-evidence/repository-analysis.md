# Repository Analysis

## Story Understanding

Story 0166 requests a deterministic, provider-neutral Tradeability capability
for Market Thesis. Its purpose is to prevent a directionally coherent thesis
from becoming a fresh `CANDIDATE` when current quote quality, spread,
liquidity/order-book evidence, market-range context, or time-sensitive News
facts are unsafe, stale, unavailable, or insufficient.

The Story is explicitly limited to Market Intelligence composition. It must
preserve typed evidence, missingness, freshness, cutoffs, provenance, and
fingerprints. It must not become a Risk, TradePlan, execution, broker, or human
authorization authority. It also excludes new providers, provider-specific
contracts, AI interpretation, frontend work, and new services.

## Repository Summary

The repository is a multi-service Trading OS platform. For this Story, Market
Intelligence owns deterministic analysis orchestration and Market Thesis
composition. Market Data owns normalized market facts and freshness. News
Service owns normalized News and economic-event facts. Risk and Trading Core
remain compatibility boundaries only.

The current authority chain is consistent with ADR-076:

```text
market facts -> deterministic analyses -> observations/typed evidence
-> Market Thesis -> downstream strategy/opportunity -> TradePlan -> Risk
-> human authorization -> execution
```

## Affected Modules

- `market-intelligence`: primary implementation and Market Thesis composition.
  It contains the current Thesis composer, context contributors, deterministic
  capabilities, evidence contracts, and related tests.
- `market-data`: source of normalized market snapshots, freshness, historical
  OHLC, and order-book replay facts. Its authority and provider-neutral
  contracts must remain unchanged.
- `news-service`: source of normalized News and economic-event facts and
  temporal metadata. No provider payloads should cross the Thesis contract.
- `trading-core`: provenance compatibility only if downstream references need
  adjustment; it must not acquire Thesis or Tradeability authority.
- `risk-domain`: compatibility verification only; financial authorization
  semantics are out of scope.

No frontend module or new microservice is affected by the approved Story scope.

## Existing Implementation

### Market snapshot and spread

`MarketSnapshotContextContributor` obtains normalized snapshots through
`MarketDataClient`, preserves occurrence/capture provenance, and maps Market
Data statuses into context sections. `QuoteQualityCalculator` deterministically
calculates midpoint, absolute spread, relative spread, and spread basis points.
`SpreadAnalysisCapability` version `1.1.0` consumes `MARKET_SNAPSHOT` and
produces `SPREAD_ANALYSIS`.

Relevant files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketSnapshotContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/QuoteQualityCalculator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/SpreadAnalysisCapability.java`
- `market-data/src/main/java/com/hope/trading/market_data/dto/MarketPriceSnapshot.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/MarketPriceSnapshotService.java`

### Order-book and liquidity

`OrderBookContextContributor` supports strict cutoff-bounded replay at bounded
depth and preserves sequence, timestamps, and source evidence fingerprints.
`OrderBookLiquidityCapability` calculates visible quantities, spread, and
liquidity-related measurements. The current replay contract exposes
`AVAILABLE`, `INVALID`, and `UNAVAILABLE`; explicit stale or insufficient
Tradeability semantics are not yet a direct order-book replay status and may
need deterministic derivation from the existing timestamps and evidence.

Relevant files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/OrderBookContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/OrderBookReplayClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/OrderBookContext.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/OrderBookLiquidityCapability.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/orderbook/OrderBookLiquidityCalculator.java`

### OHLC range

`HistoricalOhlcClient` and the normalized historical OHLC contracts preserve
cutoff, closed-candle, synthetic-data, gap, source-time, and fingerprint
information. `OhlcRangeAnalysisCapability` version `1.1.0` calculates high,
low, close, range, range percentage, price change, and simple return.

This is reusable market-range evidence, but the repository does not establish
a Tradeability-specific abnormal-range threshold or volatility policy.

Relevant files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/HistoricalOhlcClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/capability/OhlcRangeAnalysisCapability.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/OhlcTemporalAnalysis.java`

### News and economic events

`NewsContextContextContributor` retrieves normalized News context, excludes
future facts relative to the evaluation cutoff, preserves source/fetch times,
and maps unavailable, stale, unsupported, and incomplete conditions into
context status. Events retain scheduled, actual, impact, lifecycle, source
update, fetch, and normalization metadata.

The repository provides the temporal facts but no approved Thesis policy that
defines which event categories, impact values, or proximity windows are
blocking or weakening.

Relevant files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/news/NewsContextContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/news/NewsClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/NewsContext.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/NewsEventContext.java`
- `news-service/src/main/java/com/hope/trading/news/domain/EconomicEvent.java`

### Market Thesis

`MarketThesisComposer` currently composes Trend Context, structural levels,
Momentum, and explicit missing Volume/Liquidity evidence. Its state policy is:

- no directional scenario or blocking contradiction: `NO_TRADE`;
- weakening Momentum: `WATCH`;
- required stale evidence: `WATCH`;
- required unavailable or unknown evidence: `NO_TRADE`;
- otherwise: `CANDIDATE`.

Spread, order-book/liquidity, OHLC range, and News context are not currently
composed into Thesis state. Trend `phase`, `regime`, and `attention` are also
not direct Market Thesis state predicates.

Relevant files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesis.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisEvidence.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisAvailability.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposerTest.java`

The existing Thesis model already supports immutable versions, evidence
polarity, explicit contradiction severity, source cutoffs, and the required
availability states. The primary missing behavior is composition and policy,
not a replacement Thesis aggregate.

## Relevant Documentation

- `AGENTS.md`
- `docs/architecture/stories/0166-deterministic-market-tradeability-evidence/story.md`
- `docs/architecture/stories/0163-market-thesis-composition/story.md`
- `docs/architecture/stories/0164-structural-levels-and-objectives/story.md`
- `docs/architecture/stories/0165-deterministic-momentum-v1/story.md`
- `docs/architecture/stories/0102-user-facing-news-economic-context/story.md`
- `docs/architecture/stories/0112A-durable-order-book-evidence-acquisition/story.md`
- `docs/architecture/stories/0113-realistic-execution-transaction-cost-research/story.md`
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`

DevLog was consulted through the Story Agent but failed with
`LLM_PROVIDER_ERROR`; no DevLog context was used. The repository remains the
authoritative source for current implementation state.

The local curated Obsidian vault was not consulted because the repository Story,
ADR, and implementation sources were sufficient for this analysis.

## Constraints

- ADR-076 keeps Market Thesis deterministic, market-scoped, evidence-led, and
  separate from Risk, TradePlan, execution, and human authorization.
- Market Data remains authoritative for normalized market facts and freshness.
- News Service remains authoritative for normalized News and economic-event
  facts.
- Existing Trend Context, Market Structure, and Momentum authorities must not be
  duplicated or semantically changed.
- Missing, stale, unavailable, unknown, insufficient, and not-implemented
  evidence must remain distinguishable and must not be converted into support or
  negative market evidence implicitly.
- Provider-specific payloads, credentials, and cTrader identifiers must not
  cross provider-neutral contracts.
- No confidence, confluence, ranking, probability, or universal score may be
  introduced.
- Existing immutable Thesis persistence and deterministic fingerprints must be
  preserved.
- No frontend, new service, Risk behavior, execution behavior, or broker
  behavior is authorized by the Story.

## Risks

- **Undefined thresholds:** The repository does not identify an authoritative
  spread or range suitability threshold. Choosing one during implementation
  would be a material domain decision and must be resolved in planning.
- **Undefined event policy:** News exposes normalized timestamps and impact, but
  no approved policy defines event relevance, proximity, or blocking severity.
- **Context integration mismatch:** Context contributors populate
  `IntelligenceContext`, while `MarketThesisComposer` primarily consumes
  persisted `Observation` payload/evidence. The bridge for durable, cutoff-safe
  Tradeability evidence is not yet established.
- **Status translation:** Order-book replay has fewer status categories than
  the Story requires. Deriving stale or insufficient states incorrectly could
  turn evidence gaps into false contradictions.
- **False authority expansion:** Treating current spread or News as an
  execution or financial authorization decision would violate ADR-076 and the
  Story boundary.
- **Contract compatibility:** Adding required Thesis evidence could alter
  existing state transitions and persistence/API payloads; existing tests and
  downstream provenance consumers must remain compatible.

## Open Questions

- What versioned spread threshold or policy determines `AVAILABLE`, weakening,
  or blocking tradeability for each supported market/instrument class?
- Is current spread/quote evidence mandatory for `CANDIDATE` in every analysis
  mode, or are there explicitly approved modes where it is `NOT_APPLICABLE`?
- Which order-book/liquidity measurements are authoritative for this Story,
  and what minimum evidence makes them sufficient rather than merely present?
- What deterministic OHLC range predicate is intended, if any, and which source
  interval is compatible with the Thesis horizon?
- Which News/economic-event facts and proximity windows are relevant to a
  Thesis, and does event presence produce `WATCH`, `NO_TRADE`, or only explicit
  missing/context evidence?
- How should Tradeability inputs be carried from `IntelligenceContext` into the
  durable `Observation` consumed by `MarketThesisComposer` without duplicating
  source authority?

## Recommendation

Requires clarification

The repository ownership and existing contracts are sufficiently understood,
but implementation planning would require assumptions about spread thresholds,
event relevance/severity, OHLC range policy, minimum liquidity evidence, and
the context-to-observation bridge. These are material deterministic policy
decisions and should be resolved before planning.

## Implementation Readiness

The repository contains the required source contracts and reusable deterministic
calculators. No blocking module or service dependency was found. However, the
Story is not ready for implementation planning until the open policy questions
are resolved and the context-to-observation integration boundary is explicitly
accepted.

No implementation files were changed. No implementation tests were executed;
the analysis was read-only. The baseline worktree remains clean apart from this
new Story artifact and its Repository Analysis.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
