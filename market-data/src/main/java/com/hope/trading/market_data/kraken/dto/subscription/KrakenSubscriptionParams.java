package com.hope.trading.market_data.kraken.dto.subscription;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KrakenSubscriptionParams(
        String channel,
        List<String> symbol,
        Integer interval,
        @JsonProperty("event_trigger")
        String eventTrigger,

        Boolean snapshot

) {
}
