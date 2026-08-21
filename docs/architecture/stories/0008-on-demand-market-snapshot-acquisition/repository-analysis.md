# Repository Analysis — Story 0008

## Story Overview

- **Story ID:** `0008`
- **Title:** On-Demand Market Snapshot Acquisition
- **Status:** Implemented locally
- **Location:** `docs/architecture/stories/0008-on-demand-market-snapshot-acquisition/story.md`

## DevLog Context

### Retrieval Path

- `DEVLOG_CONFIRMED`: native OpenClaw/Kiko MCP path via
  `mcp__devlog.get_engineering_context`

### Retrieval Metadata

- `candidateCount`: `136`
- `selectedCount`: `60`
- `usedTokens`: `4383`
- `contextDigest`: `711e166d804b2b8273da5d233f56f952a39614751dfdf31cd5050dbfa649ec44`
- `truncated`: `true`

### Selected DevLog Evidence

- `DEVLOG_CONFIRMED`: DevLog surfaced broad Market Data and
  market-intelligence architectural history around scanner orchestration,
  market-state inputs, and provider ownership.
- `DEVLOG_CONFIRMED`: DevLog reinforced that Active Scanner should consume
  Market Data capability rather than provider mechanics.
- `UNDER_REPRESENTED`: recent Story 0006 and Story 0007 runtime findings were
  not surfaced with the same precision as the current repository truth.
- `INFERENCE`: DevLog remained useful for intent reconstruction, but repository
  code and runtime evidence were authoritative for Story 0008.

## Governing Invariants

### ADR-033

- `SOURCE_CONFIRMED`: Active Scanner is orchestration above single-market
  analysis, not a second data-acquisition system.
- `SOURCE_CONFIRMED`: Active and Passive orchestration must not fork the core
  analysis primitives.
- `SOURCE_CONFIRMED`: data/provider concerns belong below the analysis
  orchestration layer.

### Story 0006

- `SOURCE_CONFIRMED`: Story 0006 owns durable Active Scan creation, child
  registration, linkage, dispatch and retry-safe orchestration.
- `SOURCE_CONFIRMED`: Story 0008 must not redesign those semantics.

### Story 0007

- `SOURCE_CONFIRMED`: Story 0007 owns aggregate scan lifecycle reconciliation
  and trader-facing projection.
- `SOURCE_CONFIRMED`: Story 0008 must improve data availability beneath
  `AnalysisExecution`, not alter scan projection ownership semantics.

## Current Market Data Architecture Before Story 0008

### Domain / Persistence

Sources:

- `market-data/.../model/Market.java`
- `market-data/.../model/MarketState.java`
- `market-data/.../model/TickerEvent.java`
- `market-data/.../model/PriceObservation.java`

`SOURCE_CONFIRMED`:

- market identity is persisted in Market Data;
- current live ticker state was held in-memory inside `TickerEventPublisher`;
- persisted `PriceObservation` rows existed for ticker events but were not the
  authoritative low-latency current-state source;
- current snapshot reads were normalized through `MarketPriceSnapshot`.

### Internal Snapshot API

Sources:

- `market-data/.../controller/InternalMarketController.java`
- `market-data/.../service/MarketPriceSnapshotService.java`

`SOURCE_CONFIRMED`:

- Market Data exposes `POST /internal/markets/prices/snapshot`;
- both Trading Core and Market Intelligence consume that internal snapshot API;
- prior implementation used only `TickerEventPublisher.latestByMarketId(...)`
  and returned:
  - `AVAILABLE`
  - `PRICE_UNAVAILABLE`
  - `UNKNOWN_MARKET`

### Market Intelligence Consumption

Sources:

- `market-intelligence/.../adapter/marketdata/MarketDataClient.java`
- `market-intelligence/.../adapter/marketdata/MarketSnapshotContextContributor.java`
- `market-intelligence/.../application/capability/SpreadAnalysisCapability.java`

`SOURCE_CONFIRMED`:

- ACTIVE analysis requested current snapshot context through Market Data;
- `spread-analysis` required normalized market snapshot context;
- non-available snapshot responses became missing context in Market
  Intelligence;
- missing context later manifested as planning failure.

### Kraken Adapter Boundary

Sources:

- `market-data/.../brokerClient/MarketDataProvider.java`
- `market-data/.../kraken/brokerClient/KrakenMarketData.java`
- `market-data/.../kraken/brokerClient/KrakenHttpClient.java`

`SOURCE_CONFIRMED` before Story 0008:

- provider boundary supported market catalog and OHLC retrieval;
- no provider-independent one-shot current snapshot acquisition method existed;
- current runtime path depended on websocket-originated ticker events.

## Proven Runtime Defect

`RUNTIME_CONFIRMED`:

Cold runtime behavior before Story 0008:

`AnalysisExecution(ACTIVE)`  
↓  
`MarketSnapshotContextContributor` requests current snapshot  
↓  
Market Data cache empty because no ticker subscription had been primed  
↓  
snapshot context unavailable  
↓  
required artifact `normalized-market-snapshot` absent  
↓  
`ExecutionPlanner` fails with `NO_COMPATIBLE_ARTIFACT_PRODUCER`

`RUNTIME_CONFIRMED`:

Manually subscribing ETH/USD to the ticker stream in the old runtime allowed
the same downstream ACTIVE analysis path to succeed, proving that the deeper
analysis pipeline was not the primary defect.

## Story 0008 Repository Changes

### Snapshot Status Model

Sources:

- `market-data/.../dto/MarketPriceSnapshotStatus.java`
- `trading-core/.../market_data/dto/MarketPriceSnapshotStatus.java`

`SOURCE_CONFIRMED`:

status vocabulary now becomes:

- `FRESH`
- `STALE`
- `UNAVAILABLE`
- `UNKNOWN_MARKET`

### Provider Abstraction

Source:

- `market-data/.../brokerClient/MarketDataProvider.java`

`SOURCE_CONFIRMED`:

- provider boundary now includes one-shot current snapshot acquisition;
- application-level cache and fallback policy remain in
  `MarketPriceSnapshotService`;
- Kraken-specific mapping remains in the Kraken adapter boundary.

### Shared Current-State Cache

Source:

- `market-data/.../service/TickerEventPublisher.java`

`SOURCE_CONFIRMED`:

- websocket events and REST-acquired current snapshots now converge into the
  same in-memory normalized current-state representation;
- `publish(...)` remains the streaming path;
- `recordCurrentState(...)` updates the same cache without inventing a second
  cache authority.

### Freshness Ownership

Sources:

- `market-data/.../service/MarketPriceSnapshotService.java`
- `market-data/.../resources/application.properties`
- `market-intelligence/.../adapter/marketdata/MarketDataSectionFactory.java`

`SOURCE_CONFIRMED`:

- Market Data now owns current-snapshot freshness evaluation;
- freshness TTL is configured through
  `market-data.snapshot.stale-after=${MARKET_DATA_SNAPSHOT_STALE_AFTER:30s}`;
- Market Intelligence snapshot context now preserves Market Data freshness
  rather than recomputing its own conflicting current-price staleness rule.

### Bounded Failure Integration

Sources:

- `market-intelligence/.../application/execution/AnalysisContextUnavailableException.java`
- `market-intelligence/.../application/execution/CapabilityAnalysisCoordinator.java`
- `market-intelligence/.../application/execution/LocalAnalysisExecutionDispatcher.java`

`SOURCE_CONFIRMED`:

- ACTIVE analysis now fails early and truthfully when the current snapshot is
  unavailable or stale;
- missing or stale market snapshot no longer bubbles up as a misleading
  producer-architecture failure.

## Kraken Symbol Mapping

Sources:

- `market-data/.../kraken/helper/KrakenProviderSymbolResolver.java`
- `market-data/.../kraken/brokerClient/KrakenMarketData.java`

`SOURCE_CONFIRMED`:

- Story 0008 centralizes Kraken REST pair resolution in one helper;
- that helper is used by both one-shot current snapshot acquisition and OHLC;
- current implementation resolves pairs from canonical market symbol by
  removing `/`, e.g. `ETH/USD -> ETHUSD`, `XBT/EUR -> XBTEUR`.

`RUNTIME_CONFIRMED`:

- after the Story 0008 changes and rebuild, `XBT/EUR` current snapshot
  acquisition returned `FRESH` with bid/ask;
- a real ACTIVE analysis on `XBT/EUR` completed and reached
  `PipelineRun` creation.

## Query / Runtime Notes

`RUNTIME_CONFIRMED`:

- `market-data` rebuilt and restarted cleanly;
- `market-intelligence` rebuilt and restarted cleanly;
- `market-intelligence` remained internal-only and not host exposed;
- direct internal ACTIVE analysis proved the corrected snapshot dependency;
- the full authenticated Gateway plus `trading-core` account flow remained
  partly blocked by an unrelated runtime/schema issue in `trading-core`.

## Compatibility Risks

- `INFERENCE`: `TickerEventPublisher.recordCurrentState(...)` currently runs
  inside snapshot-read flow and updates the shared in-memory state as intended.
- `INFERENCE`: persisted price-observation behavior after one-shot acquisition
  should be monitored separately because the functional story acceptance does
  not depend on that persistence path.
- `SOURCE_CONFIRMED`: Story 0006 and Story 0007 semantics remain unchanged
  because Story 0008 only changes current-state availability beneath analysis.

## Migration Assessment

Verdict:

- `NO_MIGRATION_REQUIRED`

Reason:

- snapshot status remains represented in DTO/application logic;
- no schema change is required to support one-shot acquisition, freshness
  classification, or bounded snapshot failures.
