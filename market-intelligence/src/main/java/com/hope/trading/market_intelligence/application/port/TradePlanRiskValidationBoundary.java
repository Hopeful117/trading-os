package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.*;

public interface TradePlanRiskValidationBoundary {
    TradePlan loadAcceptedSnapshot(TradePlanId id, TradePlanVersion version);
    TradePlan recordRiskValidated(TradePlanId id, TradePlanVersion acceptedVersion);
    TradePlan markReadyToExecute(TradePlanId id, TradePlanVersion validatedVersion);
}
