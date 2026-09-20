package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.application.scope.DecisionContextResolution;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityDecision;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionContextResponse(
        AccountResponse account,
        List<MarketDecisionResponse> markets,
        List<UUID> eligibleMarketIds,
        Instant resolvedAt
) {
    static DecisionContextResponse from(DecisionContextResolution value) {
        return new DecisionContextResponse(
                AccountResponse.from(value.account()),
                value.scope().decisions().stream().map(MarketDecisionResponse::from).toList(),
                value.scope().effectiveScope().marketIds(),
                value.scope().resolvedAt()
        );
    }

    public record AccountResponse(
            UUID accountId,
            UUID brokerAccountId,
            String name,
            String baseCurrency,
            BigDecimal equity,
            BigDecimal peakEquity,
            UUID rulesId,
            UUID userId,
            UUID riskProfileId,
            String riskProfileSemanticVersion,
            UUID tradePlanningProfileId,
            Long tradePlanningProfileVersion
    ) {
        static AccountResponse from(TradingCoreAccountClient.TradingCoreAccountResponse value) {
            return new AccountResponse(
                    value.accountId(),
                    value.brokerAccountId(),
                    value.name(),
                    value.baseCurrency(),
                    value.equity(),
                    value.peakEquity(),
                    value.rulesId(),
                    value.userId(),
                    value.riskProfileId(),
                    value.riskProfileSemanticVersion(),
                    value.tradePlanningProfileId(),
                    value.tradePlanningProfileVersion()
            );
        }
    }

    public record MarketDecisionResponse(
            UUID marketId,
            String symbol,
            String provider,
            boolean eligible,
            List<MarketEligibilityReason> reasons
    ) {
        static MarketDecisionResponse from(MarketEligibilityDecision value) {
            return new MarketDecisionResponse(
                    value.marketId(),
                    value.symbol(),
                    value.provider(),
                    value.eligible(),
                    value.reasons()
            );
        }
    }
}
