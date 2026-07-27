package com.hope.trading.market_data.kraken.dto.subscription;

public record KrakenSubscriptionRequest(

        String method,
        KrakenSubscriptionParams params

) {
}
