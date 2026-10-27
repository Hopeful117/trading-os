package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionResult;

public record DecisionContextResolution(
        TradingCoreAccountClient.TradingCoreAccountResponse account,
        ActiveScanScopeResolutionResult scope,
        TradingCoreAccountClient.ChallengeReadinessResponse readiness,
        String scopeStatus,
        String scopeMessage
) {
    public DecisionContextResolution(TradingCoreAccountClient.TradingCoreAccountResponse account,
                                     ActiveScanScopeResolutionResult scope) {
        this(account, scope, null);
    }

    public DecisionContextResolution(TradingCoreAccountClient.TradingCoreAccountResponse account,
                                     ActiveScanScopeResolutionResult scope,
                                     TradingCoreAccountClient.ChallengeReadinessResponse readiness) {
        this(account, scope, readiness, null, null);
    }
}
