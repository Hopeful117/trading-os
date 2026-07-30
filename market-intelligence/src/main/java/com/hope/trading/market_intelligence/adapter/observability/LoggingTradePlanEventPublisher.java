package com.hope.trading.market_intelligence.adapter.observability;

import com.hope.trading.market_intelligence.application.port.TradePlanEventPublisher;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent;
import org.slf4j.*;

public final class LoggingTradePlanEventPublisher implements TradePlanEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            LoggingTradePlanEventPublisher.class);
    @Override public void publish(TradePlanEvent event) {
        LOGGER.info("trade_plan_event type={} planId={} version={}",
                event.getClass().getSimpleName(), event.planId().value(), event.version().value());
    }
}
