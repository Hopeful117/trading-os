package com.hope.trading.market_intelligence.domain.feature;

import java.util.Objects;

public record CategoricalFeatureValue(String name, FeatureMissingness status, String value)
        implements FeatureValue {
    public CategoricalFeatureValue {
        name = required(name);
        Objects.requireNonNull(status, "status");
        if (status == FeatureMissingness.PRESENT && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("Present feature requires a category");
        }
        if (status != FeatureMissingness.PRESENT && value != null) {
            throw new IllegalArgumentException("Missing feature must not have a value");
        }
        if (value != null) value = value.trim();
    }

    private static String required(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return value;
    }
}
