package com.hope.trading.market_data.kraken.dto;

public record KrakenSubscriptionRequest(

        String method,
        KrakenSubscriptionParams params

) {
}
