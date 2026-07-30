package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import java.util.*;

public final class TradePlanLifecyclePolicy {
    private static final Map<TradePlanStatus, Set<TradePlanStatus>> ALLOWED = Map.of(
            TradePlanStatus.DRAFT, EnumSet.of(
                    TradePlanStatus.PROPOSED, TradePlanStatus.EXPIRED),
            TradePlanStatus.PROPOSED, EnumSet.of(
                    TradePlanStatus.ACCEPTED, TradePlanStatus.REJECTED, TradePlanStatus.EXPIRED),
            TradePlanStatus.ACCEPTED, EnumSet.of(
                    TradePlanStatus.RISK_VALIDATED, TradePlanStatus.EXPIRED),
            TradePlanStatus.RISK_VALIDATED, EnumSet.of(
                    TradePlanStatus.READY_TO_EXECUTE, TradePlanStatus.EXPIRED),
            TradePlanStatus.READY_TO_EXECUTE, EnumSet.of(
                    TradePlanStatus.EXECUTED, TradePlanStatus.EXPIRED));
    public void validate(TradePlanStatus source, TradePlanStatus target) {
        if (!ALLOWED.getOrDefault(source, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal TradePlan transition: " + source + " -> " + target);
        }
    }
}
