package com.hope.trading.trading_core.model;

import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TradeFinancialContractTest {
    @Test
    void copiesTheValidatedContractSnapshotOntoThePersistedTradeProjection() {
        Instant observedAt = Instant.parse("2026-10-31T12:00:00Z");
        InstrumentFinancialContract contract = new InstrumentFinancialContract(
                "EURUSD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
                QuantityUnit.UNDERLYING_UNITS, new BigDecimal("100000"), 5, 2,
                new BigDecimal("0.01"), new BigDecimal("0.01"),
                new FinancialContractProvenance("market-data", 7, observedAt));

        Trade trade = new Trade();
        trade.applyFinancialContract(contract);

        assertThat(trade.getProductType()).isEqualTo(InstrumentProductType.CFD);
        assertThat(trade.getBaseCurrency()).isEqualTo("EUR");
        assertThat(trade.getQuoteCurrency()).isEqualTo("USD");
        assertThat(trade.getQuantityUnit()).isEqualTo(QuantityUnit.UNDERLYING_UNITS);
        assertThat(trade.getContractMultiplier()).isEqualByComparingTo("100000");
        assertThat(trade.getPricePrecision()).isEqualTo(5);
        assertThat(trade.getQuantityPrecision()).isEqualTo(2);
        assertThat(trade.getMinimumQuantity()).isEqualByComparingTo("0.01");
        assertThat(trade.getQuantityIncrement()).isEqualByComparingTo("0.01");
        assertThat(trade.getContractSourceId()).isEqualTo("market-data");
        assertThat(trade.getContractSourceVersion()).isEqualTo(7L);
        assertThat(trade.getContractObservedAt()).isEqualTo(observedAt);
    }
}
