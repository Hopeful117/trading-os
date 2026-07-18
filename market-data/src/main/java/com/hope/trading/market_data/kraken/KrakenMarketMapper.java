package com.hope.trading.market_data.kraken;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import org.springframework.stereotype.Component;

@Component
public class KrakenMarketMapper {
    public Market toDomain(KrakenAssetPairDto dto) {

        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(dto.getWsname())
                .baseAsset(dto.getBase())
                .quoteAsset(dto.getQuote())
                .marketConstraints(
                        MarketConstraints.builder()
                                .minimumOrderSize(dto.getMinimumOrderSize())
                                .minimumCost(dto.getMinimumCost())
                                .tickSize(dto.getTickSize())
                                .quantityPrecision(dto.getQuantityPrecision())
                                .pricePrecision(dto.getPricePrecision())
                                .build()
                )
                .build();
    }



}
