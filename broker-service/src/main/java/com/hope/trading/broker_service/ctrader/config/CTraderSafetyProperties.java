package com.hope.trading.broker_service.ctrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "ctrader.safety")
public record CTraderSafetyProperties(
        boolean enabled,
        boolean executionEnabled,
        long dailyRequestBudget,
        long alertThreshold,
        long hardStop,
        Duration snapshotCooldown,
        Duration snapshotLease) {

    public CTraderSafetyProperties(boolean enabled, long dailyRequestBudget, long alertThreshold,
                                   long hardStop, Duration snapshotCooldown, Duration snapshotLease) {
        this(enabled, false, dailyRequestBudget, alertThreshold, hardStop, snapshotCooldown, snapshotLease);
    }

    public long effectiveRequestLimit() {
        return Math.min(dailyRequestBudget, hardStop);
    }

    @ConstructorBinding
    public CTraderSafetyProperties {
        if (executionEnabled && !enabled) {
            throw new IllegalArgumentException("cTrader execution requires the safety guard to be enabled");
        }
        if (dailyRequestBudget <= 0 || alertThreshold <= 0 || hardStop <= 0
                || alertThreshold > hardStop || hardStop > dailyRequestBudget) {
            throw new IllegalArgumentException("Invalid cTrader safety request budget");
        }
        if (snapshotCooldown.isNegative() || snapshotLease.isZero() || snapshotLease.isNegative()) {
            throw new IllegalArgumentException("Invalid cTrader safety snapshot durations");
        }
    }
}
