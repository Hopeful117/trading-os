package com.hope.trading.trading_core.market_data.dto;

import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarketResponse {
    private UUID marketId;

    private MarketProvider provider;

    private String symbol;

    private String baseAsset;

    private String quoteAsset;

    private MarketState marketState;

    private MarketConstraints marketConstraints;
}
