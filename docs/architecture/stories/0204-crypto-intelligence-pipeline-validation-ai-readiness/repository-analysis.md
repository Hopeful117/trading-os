# Repository Analysis - Story 0204

## Repository State

- Branch: `main`, aligned with `origin/main`.
- HEAD: `ead7383a2102f0859f17bf5e8ae17030bc545737`.
- Working tree: clean before creation of the Story 0204 artifacts.
- Story 0204 did not exist; the number was available.
- `TOOLS.md` is absent, so no repository-local DevLog lifecycle mapping is
  available.
- DevLog Story Agent was queried with the Story 0204 scope and returned a
  terminal HTTP 500. This analysis continues from repository evidence.
- No cTrader call, private Kraken call, broker call or order was made.

## Authoritative Context

The following accepted decisions govern this Story:

- `docs/architecture/adr/ADR-020.md`: Market Intelligence orchestrates
  deterministic and AI capabilities over controlled context.
- `docs/architecture/adr/ADR-021.md`: AI execution is asynchronous, scoped,
  traceable and controlled by Market Intelligence.
- `docs/architecture/adr/ADR-034.md`: opportunities derive from persisted
  StrategyMatch; strategy validation is distinct from deterministic execution.
- `docs/architecture/adr/ADR-056.md`: deterministic evidence is typed,
  versioned, pure and explicit about missing/stale/invalid states.
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`:
  Market Thesis is market-scoped, deterministic, versioned, evidence-bearing
  and has no Risk or execution authority.

Stories 0198-0203 and their existing artifacts remain outside this Story's
implementation scope. Their cTrader protections and financial policy must not
be changed.

## Existing Components Reused

### Market Data

- `market-data/.../MarketController.java`: public market catalogue, OHLC and
  dynamic subscription endpoints.
- `market-data/.../MarketFactsService.java`: bounded market-facts calculation,
  duplicate/conflict handling, freshness and local bounded cache.
- `market-data/.../HistoricalOhlcHistoryService.java`: strict observed and
  captured historical OHLC access.
- `market-data/.../JpaHistoricalOhlcEvidenceRepository.java`: persisted OHLC
  evidence queries.
- `market-data/.../model/HistoricalOhlcSeries.java` and related models:
  provenance, normalization and completeness state.
- Kraken REST/WebSocket adapters under
  `market-data/src/main/java/com/hope/trading/market_data/kraken/`.

### Deterministic Intelligence

- `market-intelligence/.../ProductionIntelligencePipeline.java`: observation,
  thesis, strategy evaluation and opportunity orchestration.
- `market-intelligence/.../domain/trendcontext/TrendContextEngine.java`:
  H4/H1/M15 role analysis, structure, ATR, EMA, alignment and invalidation.
- `market-intelligence/.../domain/marketstructure/MarketStructureEngine.java`:
  confirmed swings and structural evidence.
- `market-intelligence/.../domain/momentum/MomentumCalculator.java`:
  deterministic OHLC momentum and explicit unavailable states.
- `market-intelligence/.../domain/tradeability/TradeabilityCalculator.java`:
  quote, spread and market-readiness evidence.
- Capabilities under
  `market-intelligence/.../application/capability/`.

### Market Thesis and AI Boundary

- `MarketThesisComposer.java` and `MarketThesis.java`.
- `JpaMarketThesisEntity.java` and `JpaMarketThesisRepository.java`.
- `MarketThesisController.java`.
- `AiEnginePort.java`, `AiAnalysisCommand.java`, `AiStructuredResult.java` and
  `AiAnalysisCapability.java`.
- `DisabledAiEngineAdapter.java`: confirms that no runtime AI provider exists.

### Frontend

- `trading-os-web/src/app/features/opportunities/`.
- `trading-os-web/src/app/core/models/market-thesis.model.ts`.
- `trading-os-web/src/app/core/models/opportunity.model.ts`.
- Active scan and opportunity services/components.

## Current Findings

1. The deterministic analysis layer exists and is substantially implemented.
   It includes Trend Context, Market Structure, Momentum, Tradeability, typed
   evidence and versioned outputs.
2. Market Thesis is already persisted and produced by the production pipeline;
   it is not merely a transient DTO.
3. The active scanner can be invoked explicitly and supports requested market
   scope, but canonical Kraken market IDs must be resolved from the catalogue.
4. Kraken public market data is sufficient for a crypto-only validation path.
   cTrader is not required and must remain out of the path.
5. Historical OHLC capture is disabled by default in configuration. The code
   has persisted observed-history access, but runtime sufficiency for H4/H1/M15
   must be proven with fixtures or an explicitly approved public-data run.
6. `MarketFactsService` has a local bounded cache and explicit status handling;
   it is not a distributed cache and must not be treated as one.
7. The pipeline already records degraded/no-signal execution states, but the
   scope of durable negative analytical memory must be verified before adding
   persistence. No broad persistence redesign is justified.
8. The AI boundary is contractual only. `DisabledAiEngineAdapter` returns
   unavailable and there is no LLM or Python/FastAPI runtime.
9. Angular already projects Market Thesis states and reasons. Validation should
   extend only missing projections or tests, not redesign the UI.
10. Opportunity ranking is ordering logic, not calibrated probability. Story
    0204 must not add or imply a statistical score.

## Expected Implementation Surface

The likely implementation surface is limited to targeted tests and, only if
the tests demonstrate a real gap, small changes in:

- Market Data history/readiness validation;
- Market Intelligence negative-outcome persistence or projection;
- deterministic AI-context serialization at the Market Intelligence boundary;
- Angular state projection tests or minimal display corrections.

No change is currently authorized to Trading Core, Risk Domain, Execution,
Broker Service or cTrader code.

## Risks and Open Questions

- The exact persisted Kraken identifiers for BTC/USD, ETH/USD and SOL/USD must
  be read from the runtime/catalogue fixture rather than assumed.
- The repository contains both Spot and Futures Kraken adapters; a scan scope
  must carry product identity explicitly.
- Historical evidence may be insufficient when capture is disabled; tests must
  distinguish a code-path capability from runtime data availability.
- The existing observation and pipeline repositories include both JPA and
  in-memory implementations; negative-outcome durability must be checked before
  introducing another store.
- The AI context projection must reference evidence and versions without
  duplicating raw source payloads or exposing account-sensitive data.

## Validation Plan

- Inspect and test canonical market identity and product filtering.
- Run targeted Market Data tests for OHLC chronology, gaps, incomplete/stale
  evidence and cutoff behavior.
- Run targeted Market Intelligence tests for Trend Context, Structure, Momentum,
  Tradeability, pipeline, Market Thesis, persistence and degraded/no-signal
  outcomes.
- Run targeted Angular tests for Market Thesis, contradictions and missing data.
- Run repository-standard Market Intelligence and frontend checks only after
  the implementation scope is approved.
- Do not perform public runtime calls during implementation without separate
  human approval.

## Workflow Status

Repository Analysis is complete. The next stage is Implementation Planning,
which requires explicit human approval of this Repository Analysis.

No application code has been changed for Story 0204.
