package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ConfirmedSwing(
        TrendContextRole role, SwingType type, int index, Instant pivotTime,
        BigDecimal price, Instant confirmationTime, String pivotSourceId,
        String confirmationSourceId, boolean suppressed, String suppressionReason,
        TrendContextEvidenceReference evidence) {
    public ConfirmedSwing {
        Objects.requireNonNull(role); Objects.requireNonNull(type); Objects.requireNonNull(pivotTime);
        Objects.requireNonNull(price); Objects.requireNonNull(confirmationTime);
        Objects.requireNonNull(pivotSourceId); Objects.requireNonNull(confirmationSourceId);
        Objects.requireNonNull(evidence);
    }
}
