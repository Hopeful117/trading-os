# Implementation Report — Story 0005

## Status

Implemented locally on branch `story/0005-account-aware-active-scan-scope-resolution`.

## Summary

Story 0005 now exposes a deterministic Active Scan scope-resolution slice in `market-intelligence`.

Implemented behavior:

- validates the selected account through the authoritative `trading-core` account endpoint;
- resolves the market catalog through `market-data`;
- deduplicates requested market IDs deterministically;
- excludes unknown markets with a deterministic reason;
- excludes non-tradable markets with a deterministic reason;
- returns an explicit effective scope result;
- preserves the existing single-market `AnalysisExecution` flow untouched;
- leaves `PipelineRun`, Risk Domain, trade planning, and Passive Scanner behavior unchanged.

## Modified Files

- `README.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/config/FeignAuthorizationConfiguration.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/tradingcore/TradingCoreAccountClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceExceptionHandler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanScopeResolutionRequestDto.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanScopeResolutionResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scope/ActiveScanScopeResolutionException.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scope/ActiveScanScopeResolutionService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scope/ActiveScanScopeResolutionRequest.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scope/ActiveScanScopeResolutionResult.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scope/EffectiveScanScope.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scope/MarketEligibilityDecision.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scope/MarketEligibilityReason.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scope/ActiveScanScopeResolutionServiceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanScopeResolutionControllerTest.java`

## Validation

Executed successfully:

- `../trading-core/mvnw -q -Dtest=ActiveScanScopeResolutionServiceTest,ActiveScanScopeResolutionControllerTest,AnalysisExecutionServiceTest,AnalysisExecutionStrategyTest,IntelligenceContextAssemblerTest,ObservationBuilderTest,OpportunityEngineTest test`

## Documentation Reconciliation

Documentation update: required.

The canonical `README.md` was updated to list the new deterministic scope-resolution endpoint:

- `POST /api/v1/intelligence/scans/scope`

This reflects the new backend capability and keeps the documented API aligned with the repository.

## Vault Outcome

No vault action required.

