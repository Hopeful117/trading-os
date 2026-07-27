package com.hope.trading.market_data.kraken.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


import java.util.Arrays;


public enum KrakenMessageType {
    SNAPSHOT("snapshot"),
    UPDATE("update");

    private final String value;

    KrakenMessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static KrakenMessageType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type ->
                        type.value.equalsIgnoreCase(value)
                ).findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported Kraken message type: " + value
                        )
                );
    }
}
