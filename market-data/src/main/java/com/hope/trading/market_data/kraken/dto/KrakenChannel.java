package com.hope.trading.market_data.kraken.dto;

import lombok.Getter;

@Getter
public enum KrakenChannel {
    TICKER("ticker");

    private final String value;

    KrakenChannel(String value) {
        this.value = value;
    }

}
