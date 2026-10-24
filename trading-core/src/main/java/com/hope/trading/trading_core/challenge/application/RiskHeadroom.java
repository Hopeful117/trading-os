package com.hope.trading.trading_core.challenge.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Risk-owned headroom projection; unavailable facts never become a safe value. */
public record RiskHeadroom(Status status, BigDecimal limit, BigDecimal used,
                           BigDecimal remaining, Instant evaluatedAt, String reason) {
    public enum Status { AVAILABLE, UNAVAILABLE }

    public static RiskHeadroom unavailable(String reason, Instant evaluatedAt) {
        return new RiskHeadroom(Status.UNAVAILABLE, null, null, null, evaluatedAt, reason);
    }
}
