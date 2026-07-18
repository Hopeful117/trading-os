package com.hope.trading.market_data.dto;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MarketResponse {
    private UUID marketId;

    private MarketProvider provider;

    private String symbol;

    private String baseAsset;

    private String quoteAsset;

    private MarketState marketState;

    private MarketConstraints marketConstraints;

}
