package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.dto.MarketResponse;
import com.hope.trading.market_data.model.Market;
import org.springframework.stereotype.Component;

@Component
public class MarketMapper {
    public MarketResponse toDto(Market market){
        return MarketResponse.builder()
                .marketId(market.getMarketId())
                .provider(market.getProvider())
                .symbol(market.getSymbol())
                .baseAsset(market.getBaseAsset())
                .quoteAsset(market.getQuoteAsset())
                .status(market.getStatus())
                .marketConstraints(market.getMarketConstraints())
                .build();
    }

}
