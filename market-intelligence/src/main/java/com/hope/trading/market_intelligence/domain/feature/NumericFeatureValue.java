package com.hope.trading.market_intelligence.domain.feature;

import java.math.BigDecimal;
import java.util.Objects;

public record NumericFeatureValue(String name, FeatureMissingness status, BigDecimal value)
        implements FeatureValue {
    public NumericFeatureValue {
        name = required(name);
        Objects.requireNonNull(status, "status");
        validateValue(status, value);
    }

    private static String required(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return value;
    }

    private static void validateValue(FeatureMissingness status, Object value) {
        if (status == FeatureMissingness.PRESENT && value == null) {
            throw new IllegalArgumentException("Present feature requires a value");
        }
        if (status != FeatureMissingness.PRESENT && value != null) {
            throw new IllegalArgumentException("Missing feature must not have a value");
        }
    }
}
