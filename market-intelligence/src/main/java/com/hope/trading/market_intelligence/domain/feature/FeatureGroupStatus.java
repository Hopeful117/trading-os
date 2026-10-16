package com.hope.trading.market_intelligence.domain.feature;

import java.util.List;
import java.util.Objects;

public record FeatureGroupStatus(FeatureGroup group, FeatureMissingness status, List<String> diagnostics) {
    public FeatureGroupStatus {
        Objects.requireNonNull(group);
        Objects.requireNonNull(status);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
