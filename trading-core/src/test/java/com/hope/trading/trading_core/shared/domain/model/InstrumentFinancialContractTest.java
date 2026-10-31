package com.hope.trading.trading_core.shared.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentFinancialContractTest {
    private static final FinancialContractProvenance PROVENANCE = new FinancialContractProvenance(
            "market-data:ctrader:EURUSD:CFD", 7, Instant.parse("2026-10-31T00:00:00Z"));

    @Test
    void acceptsCanonicalEurUsdCfdContract() {
        var contract = new InstrumentFinancialContract(
                "EUR/USD", InstrumentProductType.CFD,
                new CurrencyCode("EUR"), new CurrencyCode("USD"), QuantityUnit.UNDERLYING_UNITS,
                BigDecimal.ONE, 5, 0, new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE);

        assertThat(contract.instrument()).isEqualTo("EUR/USD");
        assertThat(contract.baseCurrency().value()).isEqualTo("EUR");
        assertThat(contract.quoteCurrency().value()).isEqualTo("USD");
        assertThat(contract.quantityUnit()).isEqualTo(QuantityUnit.UNDERLYING_UNITS);
        assertThat(contract.contractMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(contract.provenance()).isEqualTo(PROVENANCE);
    }

    @Test
    void requiresCfdWhenCfdCalculationRequestsTheContract() {
        var contract = valid("EUR/USD", InstrumentProductType.SPOT, QuantityUnit.UNDERLYING_UNITS,
                BigDecimal.ONE, 5, 0, new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE);

        assertThatThrownBy(() -> contract.requireProductType(InstrumentProductType.CFD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not compatible");
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidContracts")
    void failsClosedForInvalidContractMetadata(String reason, Supplier<InstrumentFinancialContract> contract) {
        assertThatThrownBy(contract::get)
                .isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> invalidContracts() {
        return Stream.of(
                Arguments.of("missing instrument", (Supplier<InstrumentFinancialContract>) () -> valid(null,
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                        new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("missing product type", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        null, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0, new BigDecimal("1000"),
                        new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("missing base currency", (Supplier<InstrumentFinancialContract>) () ->
                        new InstrumentFinancialContract("EUR/USD", InstrumentProductType.CFD, null,
                                new CurrencyCode("USD"), QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                                new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("missing quote currency", (Supplier<InstrumentFinancialContract>) () ->
                        new InstrumentFinancialContract("EUR/USD", InstrumentProductType.CFD,
                                new CurrencyCode("EUR"), null, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                                new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("missing quantity unit", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, null, BigDecimal.ONE, 5, 0, new BigDecimal("1000"),
                        new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("zero multiplier", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ZERO, 5, 0,
                        new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("negative multiplier", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, new BigDecimal("-1"), 5, 0,
                        new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("negative price precision", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, -1, 0,
                        new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("negative quantity precision", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, -1,
                        new BigDecimal("1000"), new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("zero minimum", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                        BigDecimal.ZERO, new BigDecimal("1000"), PROVENANCE)),
                Arguments.of("zero increment", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                        new BigDecimal("1000"), BigDecimal.ZERO, PROVENANCE)),
                Arguments.of("missing provenance", (Supplier<InstrumentFinancialContract>) () -> valid("EUR/USD",
                        InstrumentProductType.CFD, QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0,
                        new BigDecimal("1000"), new BigDecimal("1000"), null)));
    }

    private static InstrumentFinancialContract valid(String instrument, InstrumentProductType productType,
                                                     QuantityUnit quantityUnit, BigDecimal multiplier,
                                                     int pricePrecision, int quantityPrecision,
                                                     BigDecimal minimumQuantity, BigDecimal quantityIncrement,
                                                     FinancialContractProvenance provenance) {
        return new InstrumentFinancialContract(instrument, productType,
                new CurrencyCode("EUR"), new CurrencyCode("USD"), quantityUnit, multiplier,
                pricePrecision, quantityPrecision, minimumQuantity, quantityIncrement, provenance);
    }
}
