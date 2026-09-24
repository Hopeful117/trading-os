package com.hope.trading.market_intelligence.domain.observation;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Optional typed extension carried by the generic immutable observation envelope. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public interface ObservationPayload {
}
