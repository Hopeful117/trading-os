# Repository Analysis - Story 0069

## Scope

Story 0069 adds a bounded Market Facts capability inside the existing
`market-data` service. It provides deterministic Market Activity and Market
Data Readiness facts without adding candidate ranking, strategy, risk, or
execution behavior.

## Repository Evidence

Before implementation, the repository provided:

- `OhlcEvent` with provider, market, interval, volume, VWAP, close state,
  synthetic flag, source identifier, and fetch timestamp;
- `OhlcHistoryNormalizer` with sorting, deduplication, and synthetic continuity
  candle generation;
- `MarketHistoryService` with bounded OHLC requests capped at 720 events;
- the broker-neutral `MarketDataProvider` port;
- Kraken-specific acquisition and mapping in `KrakenMarketData` and
  `KrakenRestOhlcMapper`;
- JPA/Flyway persistence for catalogue and price/valuation data, but no OHLC
  history repository or application cache;
- an existing internal controller/security boundary under `/internal/**`;
- `market-intelligence` OHLC acquisition through `MarketDataClient`, with no
  existing Market Facts contract.

The DevLog applicability check was attempted for project `trading-os` and
failed with `Error invoking method: execute`. Repository state and the approved
Story were therefore the authoritative implementation sources.

## Responsibility Boundary

- `market-data` owns provider-normalized public facts and source/freshness
  metadata.
- Market Facts owns deterministic activity/readiness derivation and bounded
  reuse inside `market-data`.
- Provider adapters own provider payload mapping and acquisition.
- `market-intelligence` remains the owner of analytical evidence and future
  Candidate Selection.
- Trading Core, Risk, and Broker Service remain outside this Story.

## Implementation Boundary

Included:

- observed-versus-normalized OHLC snapshot;
- Market Activity and Market Data Readiness domain contracts;
- bounded fact service and process-local cache;
- internal versioned HTTP contract;
- focused service and controller tests.

Excluded:

- durable OHLC history storage;
- catalogue-wide scanning or ranking;
- Market Intelligence consumer integration beyond contract readiness;
- spread, liquidity, volatility, trend, AI, risk, or execution behavior.

## Architectural Assessment

No new ADR was required. The implementation remains within the boundaries of
ADR-006, ADR-010, ADR-014, ADR-033, and ADR-048. A future durable or shared
fact-store decision would require a separate architectural review if it
materially changes Market Data ownership or persistence architecture.
