package com.hope.trading.market_data.model;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum OhlcInterval {
    ONE_MINUTE(1, Duration.ofMinutes(1)),
    FIVE_MINUTES(5, Duration.ofMinutes(5)),
    FIFTEEN_MINUTES(15, Duration.ofMinutes(15)),
    THIRTY_MINUTES(30, Duration.ofMinutes(30)),
    ONE_HOUR(60, Duration.ofHours(1)),
    FOUR_HOURS(240, Duration.ofHours(4)),
    ONE_DAY(1440, Duration.ofDays(1));

    private final int minutes;
    private final Duration duration;

    OhlcInterval(int minutes, Duration duration) {
        this.minutes = minutes;
        this.duration = duration;
    }



    public static OhlcInterval fromMinutes(int minutes) {
        for (OhlcInterval interval : values()) {
            if (interval.minutes == minutes) {
                return interval;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported OHLC interval: " + minutes
        );
    }
}
