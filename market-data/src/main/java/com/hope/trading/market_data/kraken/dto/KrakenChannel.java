package com.hope.trading.market_data.kraken.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum KrakenChannel {
    TICKER("ticker"),
    OHLC("ohlc"),
    TRADES("trades"),
    ORDER_BOOK("book");

    private final String value;

    KrakenChannel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static KrakenChannel fromValue(String value) {
        return Arrays.stream(values())
                .filter(channel ->
                        channel.value.equalsIgnoreCase(value)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported Kraken channel: " + value
                        )
                );
    }

}
