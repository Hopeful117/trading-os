package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;

@FunctionalInterface
public interface TradePlanIdentifierGenerator { TradePlanId next(); }
