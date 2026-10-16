package com.hope.trading.market_intelligence.domain.feature;

import java.util.Objects;

public record BooleanFeatureValue(String name, FeatureMissingness status, Boolean value)
        implements FeatureValue {
    public BooleanFeatureValue {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(status, "status");
        if (status == FeatureMissingness.PRESENT && value == null) {
            throw new IllegalArgumentException("Present feature requires a value");
        }
        if (status != FeatureMissingness.PRESENT && value != null) {
            throw new IllegalArgumentException("Missing feature must not have a value");
        }
    }
}
