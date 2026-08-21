package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.model.Market;
import org.springframework.stereotype.Component;

@Component
public class KrakenProviderSymbolResolver {
    public String toRestPair(Market market) {
        if (market == null) {
            throw new IllegalArgumentException("Market is required");
        }
        String symbol = market.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Market provider symbol is required");
        }
        return symbol.replace("/", "").trim();
    }
}
