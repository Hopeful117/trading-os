package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CfdRiskProjectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final InstrumentFinancialContract CONTRACT = new InstrumentFinancialContract(
            "EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
            QuantityUnit.UNDERLYING_UNITS, bd("10"), 5, 0, bd("1000"), bd("1000"),
            new FinancialContractProvenance("market-data:eurusd", 3, NOW.minusSeconds(10)));
    private final CfdRiskProjectionService service = new CfdRiskProjectionService();

    @Test
    void projectsMultiplierAwareProposedNotionalAndStopRisk() {
        var projection = service.projectProposed(CONTRACT, bd("1000"), bd("1.10000"), bd("1.09900"),
                bd("999"), fact("1.10100", "USD", BigDecimal.ONE), 7, "USD");

        assertThat(projection.notional().amount()).isEqualByComparingTo("11000.00000");
        assertThat(projection.stopRisk().amount()).isEqualByComparingTo("10.00000");
        assertThat(projection.notional().currency()).isEqualTo("USD");
    }

    @Test
    void projectsCrossCurrencyPositionUsingDirectedConversion() {
        var stop = new RiskFactsProvider.Stop("stop", "paper", bd("1000"), bd("1.09900"));
        var projection = service.projectPosition(CONTRACT, bd("1000"), bd("1.10000"), List.of(stop),
                fact("1.10100", "EUR", bd("0.90")), 7, "EUR");

        assertThat(projection.marketValue().amount()).isEqualByComparingTo("9909.00000");
        assertThat(projection.lossAtStop().amount()).isEqualByComparingTo("9.00000");
    }

    @Test
    void rejectsMissingDirectedConversion() {
        assertThatThrownBy(() -> service.projectProposed(CONTRACT, bd("1000"), bd("1.10000"),
                bd("1.09900"), bd("1"), fact("1.10100", "EUR", null), 7, "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversion");
    }

    @Test
    void rejectsMissingProposedProtectiveStop() {
        assertThatThrownBy(() -> service.projectProposed(CONTRACT, bd("1000"), bd("1.10000"),
                null, bd("999"), fact("1.10100", "USD", BigDecimal.ONE), 7, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protective stop");
    }

    private static MarketValuationPort.Fact fact(String mark, String reportingCurrency, BigDecimal rate) {
        boolean crossCurrency = rate != null;
        List<MarketValuationPort.ConversionLeg> legs = crossCurrency
                ? List.of(new MarketValuationPort.ConversionLeg("USD", reportingCurrency, rate,
                "BID", "fx-source", NOW.minusSeconds(5), NOW.minusSeconds(4)))
                : List.of();
        return new MarketValuationPort.Fact("INSTRUMENT", UUID.randomUUID().toString(), UUID.randomUUID(),
                null, MarketValuationPort.PriceUse.CONSERVATIVE_SELL,
                crossCurrency ? bd(mark).multiply(rate) : bd(mark), bd(mark), rate,
                "AVAILABLE", "market-source", NOW.minusSeconds(10), NOW.minusSeconds(9),
                crossCurrency ? NOW.minusSeconds(5) : null, crossCurrency ? NOW.minusSeconds(4) : null, legs);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
