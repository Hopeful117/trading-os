package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketAvailability;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentDto;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class KrakenFuturesMarketMapper {
    private static final String FLEXIBLE_FUTURES = "flexible_futures";

    private final MarketStateBuilder marketStateBuilder;

    public Market toDomain(KrakenFuturesInstrumentDto dto, boolean currentlyTradable) {
        if (dto == null || !FLEXIBLE_FUTURES.equalsIgnoreCase(dto.getType())) {
            throw new IllegalArgumentException("A Kraken flexible futures instrument is required");
        }
        if (dto.getSymbol() == null || dto.getSymbol().isBlank()) {
            throw new IllegalArgumentException("A Kraken futures symbol is required");
        }

        String[] assets = canonicalPair(dto).split("/", 2);
        BigDecimal tickSize = dto.getTickSize();

        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(canonicalPair(dto))
                .providerSymbol(dto.getSymbol())
                .baseAsset(assets[0])
                .quoteAsset(assets.length > 1 ? assets[1] : dto.getQuote())
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .marketState(marketStateBuilder.buildInitialState(
                        currentlyTradable ? MarketAvailability.AVAILABLE : MarketAvailability.UNAVAILABLE))
                .marketConstraints(MarketConstraints.builder()
                        .tickSize(tickSize)
                        .quantityPrecision(dto.getContractValueTradePrecision())
                        .pricePrecision(decimalScale(tickSize))
                        .build())
                .build();
    }

    private String canonicalPair(KrakenFuturesInstrumentDto dto) {
        if (dto.getPair() != null && dto.getPair().contains(":")) {
            return dto.getPair().replace(':', '/');
        }
        return dto.getBase() + "/" + dto.getQuote();
    }

    private Integer decimalScale(BigDecimal value) {
        return value == null ? null : Math.max(0, value.stripTrailingZeros().scale());
    }
}
