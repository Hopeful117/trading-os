package com.hope.trading.trading_core.shared.domain.financial;

import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CfdFinancialCalculatorTest {
    private static final FinancialContractProvenance PROVENANCE = new FinancialContractProvenance(
            "test:eurusd:cfd", 1, Instant.parse("2026-10-31T00:00:00Z"));
    private static final InstrumentFinancialContract EURUSD = new InstrumentFinancialContract(
            "EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
            QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
            new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE);
    private static final CfdFinancialCalculator CALCULATOR = new CfdFinancialCalculator();

    @Test
    void calculatesAbsoluteEurUsdNotionalForLongAndShort() {
        assertMoney(CALCULATOR.calculateNotionalQuote(EURUSD, bd("1000"), bd("1.10000")), "1100", "USD");
        assertMoney(CALCULATOR.calculateNotionalQuote(EURUSD, bd("-1000"), bd("1.10000")), "1100", "USD");
    }

    @Test
    void calculatesLongAndShortGrossPnlWithOneSignedFormula() {
        assertMoney(CALCULATOR.calculateGrossPnlQuote(EURUSD, bd("1000"), bd("1.10000"), bd("1.10100")), "1.00", "USD");
        assertMoney(CALCULATOR.calculateGrossPnlQuote(EURUSD, bd("1000"), bd("1.10000"), bd("1.09900")), "-1.00", "USD");
        assertMoney(CALCULATOR.calculateGrossPnlQuote(EURUSD, bd("-1000"), bd("1.10000"), bd("1.09900")), "1.00", "USD");
        assertMoney(CALCULATOR.calculateGrossPnlQuote(EURUSD, bd("-1000"), bd("1.10000"), bd("1.10100")), "-1.00", "USD");
    }

    @Test
    void calculatesAdverseStopRiskForLongAndShort() {
        assertMoney(CALCULATOR.calculateStopLossRiskQuote(EURUSD, bd("1000"), bd("1.10000"), bd("1.09900")), "1.00", "USD");
        assertMoney(CALCULATOR.calculateStopLossRiskQuote(EURUSD, bd("-1000"), bd("1.10000"), bd("1.10100")), "1.00", "USD");
    }

    @Test
    void appliesSyntheticContractMultiplierToEveryCalculation() {
        InstrumentFinancialContract contract = new InstrumentFinancialContract(
                "EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
                QuantityUnit.UNDERLYING_UNITS, bd("10"), 5, 0,
                bd("1000"), bd("1000"), PROVENANCE);

        assertMoney(CALCULATOR.calculateNotionalQuote(contract, bd("1000"), bd("1.10000")), "11000", "USD");
        assertMoney(CALCULATOR.calculateGrossPnlQuote(contract, bd("1000"), bd("1.10000"), bd("1.10100")), "10.00", "USD");
        assertMoney(CALCULATOR.calculateStopLossRiskQuote(contract, bd("1000"), bd("1.10000"), bd("1.09900")), "10.00", "USD");
    }

    @Test
    void convertsSameCurrencyWithoutAnExternalFact() {
        assertMoney(CALCULATOR.convert(money("5", "USD"), new CurrencyCode("USD"), null), "5", "USD");
    }

    @Test
    void convertsCrossCurrencyUsingExplicitDirectedFact() {
        CurrencyConversionFact fact = new CurrencyConversionFact(new CurrencyCode("USD"),
                new CurrencyCode("EUR"), bd("0.90"), PROVENANCE);

        assertMoney(CALCULATOR.convert(money("10", "USD"), new CurrencyCode("EUR"), fact), "9.00", "EUR");
        assertMoney(CALCULATOR.convert(money("-10", "USD"), new CurrencyCode("EUR"), fact), "-9.00", "EUR");
    }

    @Test
    void retainsExactBigDecimalConversionPrecisionWithoutRounding() {
        CurrencyConversionFact fact = new CurrencyConversionFact(new CurrencyCode("USD"),
                new CurrencyCode("EUR"), bd("0.123456789"), PROVENANCE);

        com.hope.trading.risk.domain.Money converted = CALCULATOR.convert(
                money("10", "USD"), new CurrencyCode("EUR"), fact);

        assertMoney(converted, "1.234567890", "EUR");
        assertThat(converted.amount()).isEqualTo(new BigDecimal("1.234567890"));
        assertThat(fact.provenance()).isEqualTo(PROVENANCE);
    }

    @Test
    void exposesReportingCurrencyCalculationsWithoutRebuildingFinancialFacts() {
        CurrencyConversionFact fact = new CurrencyConversionFact(new CurrencyCode("USD"),
                new CurrencyCode("EUR"), bd("0.90"), PROVENANCE);

        assertMoney(CALCULATOR.calculateNotionalReporting(EURUSD, bd("1000"), bd("1.10000"),
                new CurrencyCode("EUR"), fact), "990.00", "EUR");
        assertMoney(CALCULATOR.calculateGrossPnlReporting(EURUSD, bd("1000"), bd("1.10000"),
                bd("1.10100"), new CurrencyCode("EUR"), fact), "0.90", "EUR");
        assertMoney(CALCULATOR.calculateStopLossRiskReporting(EURUSD, bd("1000"), bd("1.10000"),
                bd("1.09900"), new CurrencyCode("EUR"), fact), "0.90", "EUR");
    }

    @Test
    void rejectsNonCfdContractsAndUnsupportedQuantities() {
        InstrumentFinancialContract spot = new InstrumentFinancialContract(
                "EUR/USD", InstrumentProductType.SPOT, new CurrencyCode("EUR"), new CurrencyCode("USD"),
                QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                bd("1000"), bd("1000"), PROVENANCE);

        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(spot, bd("1000"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not compatible");
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(EURUSD, BigDecimal.ZERO, bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(EURUSD, bd("500"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(EURUSD, bd("1500"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesFractionalQuantityMinimumIncrementAndPrecision() {
        InstrumentFinancialContract fractional = new InstrumentFinancialContract(
                "EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
                QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 2,
                bd("0.01"), bd("0.01"), PROVENANCE);

        assertMoney(CALCULATOR.calculateNotionalQuote(fractional, bd("0.01"), bd("1.10000")),
                "0.01100", "USD");
        assertMoney(CALCULATOR.calculateNotionalQuote(fractional, bd("-0.02"), bd("1.10000")),
                "0.02200", "USD");
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(fractional, bd("0.005"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(fractional, bd("0.015"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    @Test
    void rejectsInvalidPricesStopsAndConversions() {
        assertThatThrownBy(() -> CALCULATOR.calculateNotionalQuote(EURUSD, bd("1000"), bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CALCULATOR.calculateGrossPnlQuote(EURUSD, bd("1000"), bd("1.100001"), bd("1.10100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CALCULATOR.calculateStopLossRiskQuote(EURUSD, bd("1000"), bd("1.10000"), bd("1.10100")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("long stop");
        assertThatThrownBy(() -> CALCULATOR.calculateStopLossRiskQuote(EURUSD, bd("-1000"), bd("1.10000"), bd("1.09900")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("short stop");
        assertThatThrownBy(() -> CALCULATOR.calculateStopLossRiskQuote(EURUSD, bd("1000"), bd("1.10000"), bd("1.10000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("long stop");
        assertThatThrownBy(() -> CALCULATOR.convert(money("10", "USD"), new CurrencyCode("EUR"), null))
                .isInstanceOf(IllegalArgumentException.class);
        CurrencyConversionFact wrongDirection = new CurrencyConversionFact(new CurrencyCode("EUR"),
                new CurrencyCode("USD"), bd("1.10"), PROVENANCE);
        assertThatThrownBy(() -> CALCULATOR.convert(money("10", "USD"), new CurrencyCode("EUR"), wrongDirection))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("direction");
    }

    @Test
    void rejectsInvalidConversionFacts() {
        assertThatThrownBy(() -> new CurrencyConversionFact(new CurrencyCode("USD"),
                new CurrencyCode("EUR"), BigDecimal.ZERO, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrencyConversionFact(new CurrencyCode("USD"),
                new CurrencyCode("EUR"), BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static com.hope.trading.risk.domain.Money money(String amount, String currency) {
        return new com.hope.trading.risk.domain.Money(bd(amount), currency);
    }

    private static void assertMoney(com.hope.trading.risk.domain.Money actual,
                                    String amount,
                                    String currency) {
        assertThat(actual.amount()).isEqualByComparingTo(amount);
        assertThat(actual.currency()).isEqualTo(currency);
    }
}
