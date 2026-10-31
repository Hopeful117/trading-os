package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/** Resolves canonical financial metadata from the Market Data boundary. */
@Service
public class InstrumentFinancialContractResolver {
    private final MarketDataClient marketData;
    private final InstrumentFinancialContractMapper mapper;

    public InstrumentFinancialContractResolver(MarketDataClient marketData,
                                               InstrumentFinancialContractMapper mapper) {
        this.marketData = Objects.requireNonNull(marketData);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public InstrumentFinancialContract resolve(String instrument) {
        return uniqueMarket(instrument)
                .map(mapper::fromMarket)
                .orElseThrow(() -> new IllegalStateException("financial contract market is unavailable"));
    }

    public InstrumentFinancialContract resolveIfCfd(String instrument) {
        return resolveForInstrument(instrument).orElse(null);
    }

    public Optional<InstrumentFinancialContract> resolveForInstrument(String instrument) {
        return uniqueMarket(instrument)
                .map(market -> market.getProductType() == InstrumentProductType.CFD
                        ? mapper.fromMarket(market) : null);
    }

    private java.util.Optional<MarketResponse> uniqueMarket(String instrument) {
        if (instrument == null || instrument.isBlank()) {
            throw new IllegalArgumentException("instrument is required");
        }
        return marketData.findAll().stream()
                .filter(market -> market != null && instrument.equalsIgnoreCase(market.getSymbol()))
                .reduce((left, right) -> {
                    throw new IllegalStateException("financial contract market identity is ambiguous");
                })
                .or(() -> { throw new IllegalStateException("financial contract market is unavailable"); });
    }
}
