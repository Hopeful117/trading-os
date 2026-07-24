package com.hope.trading.market_data.kraken.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KrakenSubscriptionParams(
        String channel,
        List<String> symbol,
        @JsonProperty("event_trigger")
        String eventTrigger,

        Boolean snapshot

) {
}
