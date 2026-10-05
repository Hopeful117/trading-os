package com.hope.trading.market_intelligence.domain.observation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Optional typed extension carried by the generic immutable observation envelope. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TrendContextObservationPayload.class, name = "trend-context-observation")
})
public interface ObservationPayload {
}
