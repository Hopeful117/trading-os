package com.hope.trading.trading_core.shared.domain.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** Immutable provider-neutral financial metadata consumed by later calculations. */
public record InstrumentFinancialContract(
        String instrument,
        InstrumentProductType productType,
        CurrencyCode baseCurrency,
        CurrencyCode quoteCurrency,
        QuantityUnit quantityUnit,
        BigDecimal contractMultiplier,
        int pricePrecision,
        int quantityPrecision,
        BigDecimal minimumQuantity,
        BigDecimal quantityIncrement,
        FinancialContractProvenance provenance) {

    public InstrumentFinancialContract {
        instrument = Objects.requireNonNull(instrument, "instrument is required").trim().toUpperCase(Locale.ROOT);
        if (instrument.isEmpty() || instrument.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("instrument identity is required");
        }
        Objects.requireNonNull(productType, "product type is required");
        Objects.requireNonNull(baseCurrency, "base currency is required");
        Objects.requireNonNull(quoteCurrency, "quote currency is required");
        if (baseCurrency.equals(quoteCurrency)) {
            throw new IllegalArgumentException("base and quote currencies must differ");
        }
        Objects.requireNonNull(quantityUnit, "quantity unit is required");
        if (contractMultiplier == null || contractMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("contract multiplier must be positive");
        }
        if (pricePrecision < 0 || quantityPrecision < 0) {
            throw new IllegalArgumentException("precision must not be negative");
        }
        if (minimumQuantity == null || minimumQuantity.signum() <= 0) {
            throw new IllegalArgumentException("minimum quantity must be positive");
        }
        if (quantityIncrement == null || quantityIncrement.signum() <= 0) {
            throw new IllegalArgumentException("quantity increment must be positive");
        }
        if (decimalPlaces(minimumQuantity) > quantityPrecision
                || decimalPlaces(quantityIncrement) > quantityPrecision) {
            throw new IllegalArgumentException("quantity constraints exceed quantity precision");
        }
        Objects.requireNonNull(provenance, "financial contract provenance is required");
    }

    private static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    public InstrumentFinancialContract requireProductType(InstrumentProductType expected) {
        if (productType != Objects.requireNonNull(expected, "expected product type is required")) {
            throw new IllegalArgumentException("instrument product type is not compatible with the requested calculation");
        }
        return this;
    }
}
