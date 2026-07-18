package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.model.MarketState;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MarketStateBuilder {
    public MarketState buildInitialState(MarketAvailability availability) {

        if (availability.equals(MarketAvailability.AVAILABLE)) {

            return MarketState.builder()
                    .tradingStatus(TradingStatus.OPEN)
                    .tradable(true)
                    .lastUpdated(Instant.now())
                    .build();

        }

        return MarketState.builder()
                .tradingStatus(TradingStatus.CLOSED)
                .tradable(false)
                .closureReason(MarketClosureReason.PROVIDER_UNAVAILABLE)
                .lastUpdated(Instant.now())
                .build();
    }
}
