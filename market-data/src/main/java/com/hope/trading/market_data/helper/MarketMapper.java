package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.dto.MarketResponse;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import org.springframework.stereotype.Component;

@Component
public class MarketMapper {
    public MarketResponse toDto(Market market){
        return toDto(market, identityResolved(market));
    }

    public MarketResponse toDto(Market market, boolean identityResolved){
        return MarketResponse.builder()
                .marketId(market.getMarketId())
                .provider(market.getProvider())
                .symbol(market.getSymbol())
                .providerSymbol(market.getProviderSymbol())
                .baseAsset(market.getBaseAsset())
                .quoteAsset(market.getQuoteAsset())
                .productType(market.getProductType())
                .identityResolved(identityResolved)
                .quantityUnit(market.getQuantityUnit())
                .contractMultiplier(market.getContractMultiplier())
                .contractSourceId(market.getContractSourceId())
                .contractSourceVersion(market.getContractSourceVersion())
                .contractObservedAt(market.getContractObservedAt())
                .marketState(identityResolved ? market.getMarketState() : unresolvedState(market))
                .marketConstraints(market.getMarketConstraints())
                .build();
    }

    private boolean identityResolved(Market market) {
        return market.getProvider() != null
                && market.getSymbol() != null && !market.getSymbol().isBlank()
                && market.getProviderSymbol() != null && !market.getProviderSymbol().isBlank()
                && market.getProductType() != null;
    }

    private MarketState unresolvedState(Market market) {
        return MarketState.builder()
                .tradingStatus(TradingStatus.UNKNOWN)
                .tradable(false)
                .closureReason(MarketClosureReason.UNKNOW)
                .lastUpdated(market.getMarketState() == null ? null : market.getMarketState().getLastUpdated())
                .build();
    }

}
