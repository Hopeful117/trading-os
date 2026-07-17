package com.hope.trading.market_data.model;


import com.hope.trading.market_data.helper.MarketStatus;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Market {

    private final String symbol;

    private final String baseAsset;

    private final String quoteAsset;

    private final MarketStatus status;

    private final MarketConstraints marketConstraints;

}


