package com.hope.trading.trading_core.shared.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyCode(String value) {
    private static final Pattern ISO_4217_CODE = Pattern.compile("^[A-Z]{3}$");

    public CurrencyCode {
        value = Objects.requireNonNull(value, "currency is required").trim().toUpperCase(Locale.ROOT);
        if (!ISO_4217_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
    }
}
