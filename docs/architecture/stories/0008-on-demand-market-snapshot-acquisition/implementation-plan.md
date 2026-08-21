# Implementation Plan — Story 0008

## Objective

Implement the smallest coherent Market Data capability that can satisfy
ACTIVE-analysis current snapshot needs in a cold runtime without requiring a
prior websocket ticker subscription.

## Architecture Decision

Story 0008 is implemented as a Market Data responsibility enhancement.

The design:

1. keeps one normalized current-state cache;
2. makes snapshot reads cache-first rather than cache-only;
3. falls back to synchronous one-shot provider acquisition when the cache is
   missing or stale;
4. returns truthful freshness semantics from Market Data;
5. keeps provider mechanics below the Market Data adapter boundary;
6. integrates bounded snapshot-availability failures into Market Intelligence
   without altering Active Scan orchestration.

## MUST_REUSE

- existing `MarketPriceSnapshot` normalized contract
- existing `TickerEventPublisher` current-state cache
- existing `MarketDataProvider` abstraction
- existing `MarketSnapshotContextContributor`
- existing `CapabilityAnalysisCoordinator`
- existing `LocalAnalysisExecutionDispatcher`
- existing Story 0006 orchestration
- existing Story 0007 projection

## MUST_NOT_MODIFY

- Story 0006 scan creation, linkage or dispatch semantics
- Story 0007 scan lifecycle projection semantics
- Active Scanner ownership model
- Gateway trust boundary
- Passive Scanner orchestration semantics
- Broker or Risk Domain logic

## MUST_NOT_DUPLICATE

- current-state cache authority
- provider-symbol mapping authority
- freshness authority
- provider fallback logic inside Market Intelligence

## Phase 1 — Normalize Snapshot Status and Freshness Ownership

### Files

- `MODIFY` `market-data/.../dto/MarketPriceSnapshotStatus.java`
- `MODIFY` `trading-core/.../market_data/dto/MarketPriceSnapshotStatus.java`
- `MODIFY` `market-data/.../resources/application.properties`
- `MODIFY` `market-intelligence/.../adapter/marketdata/MarketDataSectionFactory.java`

### Behavior

- replace fragmented snapshot availability semantics with:
  - `FRESH`
  - `STALE`
  - `UNAVAILABLE`
  - `UNKNOWN_MARKET`
- introduce Market Data TTL property for freshness authority
- preserve snapshot freshness semantics downstream rather than recomputing them
  differently in Market Intelligence

### Tests

- snapshot-status mapping tests
- dashboard/consumer compatibility tests

### Completion Condition

All consuming modules understand the new snapshot status vocabulary.

## Phase 2 — Add Provider One-Shot Current Snapshot Capability

### Files

- `MODIFY` `market-data/.../brokerClient/MarketDataProvider.java`
- `MODIFY` `market-data/.../kraken/brokerClient/KrakenHttpClient.java`
- `MODIFY` `market-data/.../kraken/brokerClient/KrakenMarketData.java`
- `NEW` `market-data/.../kraken/dto/ticker/KrakenTickerResponse.java`
- `NEW` `market-data/.../kraken/dto/ticker/KrakenRestTickerData.java`
- `NEW` `market-data/.../kraken/helper/KrakenRestTickerMapper.java`

### Behavior

- add provider-independent one-shot current snapshot acquisition
- implement Kraken public REST ticker acquisition
- normalize provider response into existing `TickerEvent`

### Tests

- Kraken response mapping
- provider error normalization
- missing bid/ask handling

### Completion Condition

Market Data can obtain a normalized current snapshot without prior websocket
activity.

## Phase 3 — Centralize Kraken REST Pair Resolution

### Files

- `NEW` `market-data/.../kraken/helper/KrakenProviderSymbolResolver.java`
- `MODIFY` `market-data/.../kraken/brokerClient/KrakenMarketData.java`

### Behavior

- centralize provider-symbol resolution for REST pair access
- reuse the same mapping boundary for both one-shot current snapshot and OHLC

### Tests

- `ETH/USD`
- `ETH/EUR`
- `XBT/EUR`

### Completion Condition

Kraken REST-facing data acquisition no longer scatters symbol transformation
logic.

## Phase 4 — Implement Cache-First Snapshot Acquisition Service

### Files

- `MODIFY` `market-data/.../service/MarketPriceSnapshotService.java`
- `MODIFY` `market-data/.../service/TickerEventPublisher.java`

### Behavior

- read shared current-state cache first
- return cached `FRESH` snapshot when valid
- fallback to provider one-shot acquisition when missing or stale
- update the same normalized current-state cache after successful acquisition
- return truthful `STALE` if only degraded cached data remains
- return `UNAVAILABLE` if no usable data exists

### Tests

- fresh cache hit
- missing cache
- stale cache refresh success
- stale cache refresh failure
- no cache + provider failure

### Completion Condition

Current snapshot reads no longer depend on prior websocket warm-up.

## Phase 5 — Add Same-Market In-Flight Deduplication

### Files

- `MODIFY` `market-data/.../service/MarketPriceSnapshotService.java`

### Behavior

- introduce per-market in-flight acquisition deduplication
- same market shares one in-process provider request
- different markets remain independent
- failed claims are removed to allow later retry

### Tests

- same-market concurrent cold requests
- different-market independence
- failed in-flight cleanup

### Completion Condition

The service avoids the most obvious single-process provider stampede.

## Phase 6 — Integrate Bounded Snapshot Failures into Market Intelligence

### Files

- `NEW` `market-intelligence/.../application/execution/AnalysisContextUnavailableException.java`
- `MODIFY` `market-intelligence/.../adapter/marketdata/MarketSnapshotContextContributor.java`
- `MODIFY` `market-intelligence/.../application/execution/CapabilityAnalysisCoordinator.java`
- `MODIFY` `market-intelligence/.../application/execution/LocalAnalysisExecutionDispatcher.java`
- `MODIFY` `market-intelligence/.../application/pipeline/AnalysisTradePlanGenerationService.java`

### Behavior

- surface missing or stale current-state as bounded data-availability failures
- prevent misleading `NO_COMPATIBLE_ARTIFACT_PRODUCER` when the producer exists
- reject stale snapshot use in ACTIVE spread-analysis planning
- preserve downstream story boundaries

### Tests

- fresh snapshot -> artifact produced
- unavailable snapshot -> bounded failure
- stale snapshot policy

### Completion Condition

ACTIVE analysis planning either receives a usable normalized market snapshot or
fails truthfully for data availability reasons.

## Phase 7 — Regression Coverage Across Affected Modules

### Files

- `MODIFY` `market-data/.../MarketPriceSnapshotServiceTest.java`
- `MODIFY` `market-data/.../KrakenMarketDataTest.java`
- `NEW` `market-intelligence/.../MarketSnapshotContextContributorTest.java`
- `NEW` `market-intelligence/.../CapabilityAnalysisCoordinatorTest.java`
- `MODIFY` `market-intelligence/.../MarketDataSectionFactoryTest.java`
- `MODIFY` `trading-core/.../DashboardQueryServiceTest.java`

### Behavior

- prove snapshot acquisition semantics
- prove consumer compatibility
- prove stale and unavailable handling
- prove same-market concurrency

### Completion Condition

Focused and full affected-module tests validate the new Market Data behavior.

## Phase 8 — Runtime Validation

### Behavior

- rebuild `market-data` and `market-intelligence`
- verify cold cache snapshot acquisition on real runtime
- verify ACTIVE single-market analysis completes without prior ticker
  subscription
- verify downstream `PipelineRun`, `Observation`, and `TradingOpportunity`
  lineage can be reached
- verify shared-cache reuse on repeated requests
- verify `XBT/EUR` runtime behavior

### Completion Condition

The previously proven runtime defect is no longer reproducible on the corrected
code path.
