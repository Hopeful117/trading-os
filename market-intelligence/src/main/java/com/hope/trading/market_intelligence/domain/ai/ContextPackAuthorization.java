package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.context.ContextConsumer;

import java.util.Objects;

public record ContextPackAuthorization(
        ContextConsumer consumer,
        ContextClassification maximumClassification
) {
    public ContextPackAuthorization {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(maximumClassification, "maximumClassification");
        if (consumer != ContextConsumer.AI_CAPABILITY) {
            throw new IllegalArgumentException("ContextPack requires AI capability authorization");
        }
        if (maximumClassification == ContextClassification.RESTRICTED) {
            throw new IllegalArgumentException("Restricted context cannot be sent to AI");
        }
    }

    public static ContextPackAuthorization publicAi() {
        return new ContextPackAuthorization(ContextConsumer.AI_CAPABILITY, ContextClassification.PUBLIC);
    }
}
