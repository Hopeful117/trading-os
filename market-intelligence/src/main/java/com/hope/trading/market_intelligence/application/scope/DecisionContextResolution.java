package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionResult;

public record DecisionContextResolution(
        TradingCoreAccountClient.TradingCoreAccountResponse account,
        ActiveScanScopeResolutionResult scope
) {
}
