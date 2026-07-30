package com.hope.trading.market_intelligence.domain.opportunity;

import java.util.Objects;
import java.util.UUID;

public record ObservationReference(UUID observationId) {
    public ObservationReference { Objects.requireNonNull(observationId, "observationId"); }
}
