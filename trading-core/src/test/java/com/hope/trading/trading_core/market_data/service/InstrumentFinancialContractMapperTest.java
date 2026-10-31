package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentFinancialContractMapperTest {
    private static final FinancialContractProvenance PROVENANCE = new FinancialContractProvenance(
            "market-data:ctrader:EURUSD:CFD", 7, Instant.parse("2026-10-31T00:00:00Z"));

    @Test
    void mapsCanonicalMarketFactsToEurUsdCfdContract() {
        var contract = new InstrumentFinancialContractMapper().fromCfdMarket(
                market(InstrumentProductType.CFD), new BigDecimal("1"), PROVENANCE);

        assertThat(contract.instrument()).isEqualTo("EUR/USD");
        assertThat(contract.productType()).isEqualTo(InstrumentProductType.CFD);
        assertThat(contract.baseCurrency().value()).isEqualTo("EUR");
        assertThat(contract.quoteCurrency().value()).isEqualTo("USD");
        assertThat(contract.quantityUnit()).isEqualTo(QuantityUnit.UNDERLYING_UNITS);
        assertThat(contract.contractMultiplier()).isEqualByComparingTo("1");
        assertThat(contract.minimumQuantity()).isEqualByComparingTo("1000");
        assertThat(contract.quantityIncrement()).isEqualByComparingTo("1000");
        assertThat(contract.pricePrecision()).isEqualTo(5);
        assertThat(contract.quantityPrecision()).isZero();
    }

    @Test
    void rejectsNonCfdMarketForCfdContractMapping() {
        assertThatThrownBy(() -> new InstrumentFinancialContractMapper()
                .fromCfdMarket(market(InstrumentProductType.SPOT), new BigDecimal("1"), PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not compatible");
    }

    @Test
    void rejectsMissingContractMultiplierInsteadOfInferringIt() {
        assertThatThrownBy(() -> new InstrumentFinancialContractMapper()
                .fromCfdMarket(market(InstrumentProductType.CFD), null, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract multiplier");
    }

    @Test
    void rejectsLegacyConstraintsWithoutQuantityIncrement() {
        MarketResponse market = MarketResponse.builder()
                .symbol("EUR/USD")
                .baseAsset("EUR")
                .quoteAsset("USD")
                .productType(InstrumentProductType.CFD)
                .marketConstraints(new MarketConstraints(new BigDecimal("1000"), null,
                        new BigDecimal("0.00001"), 0, 5))
                .build();

        assertThatThrownBy(() -> new InstrumentFinancialContractMapper()
                .fromCfdMarket(market, BigDecimal.ONE, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity increment");
    }

    private MarketResponse market(InstrumentProductType productType) {
        return MarketResponse.builder()
                .symbol("EUR/USD")
                .baseAsset("EUR")
                .quoteAsset("USD")
                .productType(productType)
                .marketConstraints(new MarketConstraints(new BigDecimal("1000"), new BigDecimal("1000"),
                        null, new BigDecimal("0.00001"), 0, 5))
                .build();
    }
}
