# Implementation Report — Story 0008

## Status

Implemented locally on branch
`story/0008-on-demand-market-snapshot-acquisition`.

## Summary

Story 0008 removes the runtime dependency between ACTIVE analysis and a
previously primed websocket ticker subscription.

Implemented behavior:

- evolves Market Data current snapshot status semantics to `FRESH`, `STALE`,
  `UNAVAILABLE`, and `UNKNOWN_MARKET`;
- introduces a Market Data-owned configurable freshness TTL through
  `market-data.snapshot.stale-after`;
- extends the provider boundary with one-shot current snapshot acquisition;
- implements Kraken public REST ticker acquisition for normalized current-state
  fallback;
- centralizes Kraken REST pair resolution through one symbol-mapping helper;
- changes current snapshot retrieval from cache-only to cache-first with
  synchronous provider fallback;
- updates the same normalized current-state cache after one-shot acquisition;
- adds same-market per-process in-flight acquisition deduplication;
- preserves websocket streaming behavior for continuous monitoring paths;
- prevents missing/stale current-market state from surfacing as misleading
  `NO_COMPATIBLE_ARTIFACT_PRODUCER` in Market Intelligence;
- introduces bounded ACTIVE analysis failures for unavailable or stale snapshot
  context;
- preserves Story 0006 orchestration and Story 0007 projection semantics;
- confirms runtime success for cold-cache ACTIVE analysis on both `ETH/USD` and
  `XBT/EUR`.

## Production Files Added

- `market-data/src/main/java/com/hope/trading/market_data/kraken/dto/ticker/KrakenRestTickerData.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/dto/ticker/KrakenTickerResponse.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/helper/KrakenProviderSymbolResolver.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/helper/KrakenRestTickerMapper.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisContextUnavailableException.java`

## Production Files Modified

- `market-data/src/main/java/com/hope/trading/market_data/brokerClient/MarketDataProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/dto/MarketPriceSnapshotStatus.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/KrakenHttpClient.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/KrakenMarketData.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/MarketPriceSnapshotService.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/TickerEventPublisher.java`
- `market-data/src/main/resources/application.properties`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataSectionFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketSnapshotContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/CapabilityAnalysisCoordinator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/LocalAnalysisExecutionDispatcher.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/AnalysisTradePlanGenerationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/DashboardQueryService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/dto/MarketPriceSnapshotStatus.java`

## Test Files Added

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketSnapshotContextContributorTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/execution/CapabilityAnalysisCoordinatorTest.java`

## Test Files Modified

- `market-data/src/test/java/com/hope/trading/market_data/kraken/brokerClient/KrakenMarketDataTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/service/MarketPriceSnapshotServiceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataSectionFactoryTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/dashboard/service/DashboardQueryServiceTest.java`

## Final Snapshot Contract

Current normalized snapshot responses expose:

- `marketId`
- `symbol`
- `lastPrice`
- `bid`
- `ask`
- `tradable`
- `occurredAt`
- `status`
- `sourceSnapshotId`
- `sourceSnapshotVersion`
- `capturedAt`

Semantics:

- `bid` and `ask` are required for a usable ACTIVE current-state artifact;
- `occurredAt` is the effective observation time used for freshness;
- `capturedAt` remains the local snapshot capture time;
- no provider-specific DTO leaks out of Market Data.

## Final Freshness Model

Implemented statuses:

- `FRESH`
- `STALE`
- `UNAVAILABLE`
- `UNKNOWN_MARKET`

Configured property:

- `market-data.snapshot.stale-after=${MARKET_DATA_SNAPSHOT_STALE_AFTER:30s}`

Story 0008 keeps the default TTL aligned with existing downstream expectations
without introducing per-capability or per-market freshness configuration.

## Acquisition Algorithm Implemented

Runtime behavior:

1. load requested market;
2. inspect shared current-state cache;
3. return cached `FRESH` snapshot if still within TTL and contains usable
   bid/ask;
4. otherwise attempt one-shot provider acquisition;
5. on success normalize to `TickerEvent`, update shared cache, and return
   `FRESH`;
6. on provider failure return `STALE` only if a previously cached usable
   snapshot exists;
7. otherwise return `UNAVAILABLE`.

## Concurrency Model

Story 0008 adds a per-process, per-market in-flight acquisition registry:

- concurrent cold requests for the same market share one provider acquisition;
- different markets remain independent;
- failed acquisitions are removed from the registry so later retry remains
  possible.

## Bounded Failure Semantics

Story 0008 intentionally separates:

- architecture truly lacks producer

from

- producer exists but current market data is unavailable or stale.

Implemented bounded failures in Market Intelligence:

- `MARKET_SNAPSHOT_UNAVAILABLE`
- `MARKET_SNAPSHOT_STALE`

These are raised before capability planning can misclassify the failure as
producer architecture absence.

## Stale-Data Policy

The implemented ACTIVE-analysis safety policy is conservative:

- stale current snapshot may be returned truthfully by Market Data;
- ACTIVE planning does not silently treat stale current bid/ask as fresh;
- stale snapshot causes bounded analysis failure rather than misleading planning
  failure.

## Kraken Mapping Verdict

Story 0008 includes a centralized Kraken REST pair mapping helper because
the same mapping boundary is used by:

- one-shot current snapshot acquisition;
- OHLC REST retrieval.

Runtime validation confirmed:

- `ETH/USD` one-shot snapshot acquisition works in a cold cache;
- `XBT/EUR` one-shot snapshot acquisition works in a cold cache;
- `XBT/EUR` ACTIVE analysis now reaches `PipelineRun` creation.

## Validation

### Focused Tests

Executed:

```bash
cd market-data && mvn -q -Dtest=MarketPriceSnapshotServiceTest,KrakenMarketDataTest test
cd market-intelligence && mvn -q -Dtest=MarketDataSectionFactoryTest,MarketSnapshotContextContributorTest,CapabilityAnalysisCoordinatorTest,AnalysisExecutionServiceTest test
cd trading-core && mvn -q -Dtest=DashboardQueryServiceTest test
```

Result:

- pass

### Full Affected-Module Suites

Executed:

```bash
cd market-data && mvn test
cd market-intelligence && mvn test
cd trading-core && mvn test
```

Result:

- `market-data`: `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`
- `market-intelligence`: `Tests run: 167, Failures: 0, Errors: 0, Skipped: 0`
- `trading-core`: one unrelated pre-existing failure in
  `RiskAcknowledgmentOutboxPersistenceTest.claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry`

## Runtime Evidence

### Cold Snapshot Proof

After rebuilding and restarting `market-data`, direct internal snapshot request
for `ETH/USD` returned:

- `status=FRESH`
- real `bid`
- real `ask`
- no prior manual ticker subscription

Immediate repeated request returned the same snapshot identity, confirming
shared-cache reuse while still fresh.

### ACTIVE Analysis Proof

Direct internal ACTIVE analysis for `ETH/USD` completed successfully:

- `AnalysisExecution.COMPLETED`
- capability execution progressed
- `ProductionIntelligencePipeline` persisted a `COMPLETED` run
- downstream `Observation` and `TradingOpportunity` lineage were created

### Kraken XBT/EUR Proof

Direct internal ACTIVE analysis for `XBT/EUR` also completed successfully:

- one-shot current snapshot returned `FRESH`
- analysis reached `PipelineRun`
- the earlier `Unknown asset pair` runtime symptom was no longer reproduced on
  this path

### Gateway / ActiveScan Full Benchmark Limitation

The full authenticated Gateway plus owned ActiveScan runtime benchmark remained
partially blocked by an unrelated `trading-core` runtime/schema issue:

- local `trading-app` compose startup required unresolved local artifact wiring
  and then failed schema validation for missing `execution_attempt`

This did not block direct runtime proof of the Story 0008 defect correction in
the real `market-data -> market-intelligence -> pipeline` path.

## Migration Verdict

- `NO_MIGRATION_REQUIRED`

## Security Impact

No trust-boundary expansion was introduced.

Story 0008:

- does not expose `market-intelligence` publicly;
- does not change Gateway actor propagation;
- does not add new actor-owned API surfaces;
- does not add Broker or Risk side effects.

## Deviation

The approved runtime benchmark preferred a full authenticated Gateway ActiveScan
path. The workstation environment blocked that end-to-end proof because of an
unrelated `trading-core` runtime/schema drift. Story 0008 was therefore
runtime-proved through the real internal ACTIVE analysis path that exercised
the exact failing snapshot dependency and downstream intelligence pipeline.

## Remaining Limitations

- no distributed cache or distributed acquisition coordination;
- no richer persisted analysis failure model;
- no broader Market Data redesign beyond current-snapshot acquisition.
