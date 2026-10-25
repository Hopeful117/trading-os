package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.tradeplan.OpportunityPlanReference;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanOrigin;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public final class TradePlanOriginContextResolver {
    private final TradingOpportunityRepository opportunities;

    public TradePlanOriginContextResolver(TradingOpportunityRepository opportunities) {
        this.opportunities = opportunities;
    }

    public TradePlanOriginContext resolve(TradePlan plan, TradePlanningContext context) {
        UUID accountId = context.tradingAccountId();
        if (plan.origin() == TradePlanOrigin.MANUAL) {
            return TradePlanOriginContext.unavailable("MANUAL", accountId, "MANUAL_PLAN");
        }

        if (plan.rationale().opportunities().size() != 1) {
            return TradePlanOriginContext.unavailable(
                    "OPPORTUNITY", accountId, "MULTIPLE_ORIGINS");
        }

        OpportunityPlanReference reference = plan.rationale().opportunities().iterator().next();
        TradingOpportunity origin = opportunities.find(reference.id(), reference.version()).orElse(null);
        if (origin == null) {
            return TradePlanOriginContext.unavailable(
                    "OPPORTUNITY", accountId, "ORIGIN_UNAVAILABLE");
        }

        if (origin.accountId().isEmpty() || !accountId.equals(origin.accountId().orElseThrow())) {
            return TradePlanOriginContext.unavailable(
                    "OPPORTUNITY", accountId, "ORIGIN_ACCOUNT_MISMATCH");
        }

        TradingOpportunity current = opportunities.findLatest(origin.id()).orElse(origin);
        UUID marketId = origin.marketId().orElse(null);
        boolean returnAvailable = marketId != null;
        return new TradePlanOriginContext(
                "OPPORTUNITY",
                accountId,
                origin.id().value(),
                origin.version().value(),
                marketId,
                origin.sourceScanId().orElse(null),
                current.status().name(),
                current.validFrom(),
                current.validUntil().orElse(null),
                returnAvailable,
                returnAvailable ? null : "MARKET_ID_UNAVAILABLE");
    }
}
