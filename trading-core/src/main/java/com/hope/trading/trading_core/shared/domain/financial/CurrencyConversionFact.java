package com.hope.trading.trading_core.shared.domain.financial;

import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicitly directed conversion fact; freshness is evaluated outside the calculator. */
public record CurrencyConversionFact(CurrencyCode sourceCurrency,
                                     CurrencyCode targetCurrency,
                                     BigDecimal rate,
                                     FinancialContractProvenance provenance) {
    public CurrencyConversionFact {
        sourceCurrency = Objects.requireNonNull(sourceCurrency, "source currency is required");
        targetCurrency = Objects.requireNonNull(targetCurrency, "target currency is required");
        if (sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("conversion currencies must differ");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("conversion rate must be positive");
        }
        Objects.requireNonNull(provenance, "conversion provenance is required");
    }
}
