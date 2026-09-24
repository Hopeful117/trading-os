package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;

import java.util.Objects;

public record TrendContextObservationPayload(
        TrendContextCapabilityContent content
) implements ObservationPayload {
    public TrendContextObservationPayload {
        Objects.requireNonNull(content, "content is required");
        if (content.assessment() == null) {
            throw new IllegalArgumentException("Trend Context observation requires an assessment");
        }
    }
}
