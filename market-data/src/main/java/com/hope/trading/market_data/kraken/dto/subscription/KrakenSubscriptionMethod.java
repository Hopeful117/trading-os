package com.hope.trading.market_data.kraken.dto.subscription;

import lombok.Getter;

@Getter
public enum KrakenSubscriptionMethod {
    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe");

    private final String value;

    KrakenSubscriptionMethod(String value) {
        this.value = value;
    }

}
