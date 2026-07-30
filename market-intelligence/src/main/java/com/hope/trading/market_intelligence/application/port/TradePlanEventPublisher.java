package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent;

@FunctionalInterface
public interface TradePlanEventPublisher { void publish(TradePlanEvent event); }
