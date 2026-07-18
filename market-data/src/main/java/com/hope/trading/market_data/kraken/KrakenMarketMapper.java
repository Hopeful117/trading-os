package com.hope.trading.market_data.kraken;

import com.hope.trading.market_data.helper.MarketAvailability;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KrakenMarketMapper {
    private final MarketStateBuilder marketStateBuilder;
    public Market toDomain(KrakenAssetPairDto dto) {

        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(dto.getWsname())
                .baseAsset(dto.getBase())
                .quoteAsset(dto.getQuote())
                .marketState(marketStateBuilder.buildInitialState("online".equalsIgnoreCase(dto.getStatus()) ? MarketAvailability.AVAILABLE : MarketAvailability.UNAVAILABLE))
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
