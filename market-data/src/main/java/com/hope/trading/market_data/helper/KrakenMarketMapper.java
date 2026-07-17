package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.dto.KrakenAssetPairDto;
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
                .status(mapStatus(dto.getStatus()))
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



    private MarketStatus mapStatus(String status) {

        return switch (status) {

            case "online" -> MarketStatus.ACTIVE;

            default -> MarketStatus.INACTIVE;
        };
    }
}
