package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@ToString
public class MarketDataEvent {
    private final MarketProvider provider;

    private final String symbol;

    private final BigDecimal bid;

    private final BigDecimal ask;

    private final BigDecimal volume;

    private final BigDecimal last;

    private final Instant timestamp;

}
