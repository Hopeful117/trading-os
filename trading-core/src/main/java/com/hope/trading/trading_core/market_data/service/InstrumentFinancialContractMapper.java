package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

    @Component
public class InstrumentFinancialContractMapper {
    public InstrumentFinancialContract fromCfdMarket(MarketResponse market,
                                                     BigDecimal contractMultiplier,
                                                     FinancialContractProvenance provenance) {
        if (market == null) throw new IllegalArgumentException("market is required");
        if (market.getProductType() != InstrumentProductType.CFD) {
            throw new IllegalArgumentException("instrument product type is not compatible with the requested calculation");
        }
        MarketConstraints constraints = market.getMarketConstraints();
        if (constraints == null) throw new IllegalArgumentException("market constraints are required");
        return new InstrumentFinancialContract(
                market.getSymbol(), market.getProductType(),
                new CurrencyCode(market.getBaseAsset()), new CurrencyCode(market.getQuoteAsset()),
                QuantityUnit.UNDERLYING_UNITS, contractMultiplier,
                required(constraints.pricePrecision(), "price precision"),
                required(constraints.quantityPrecision(), "quantity precision"),
                constraints.minimumOrderSize(), constraints.orderStepSize(), provenance);
    }

    private static int required(Integer value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
