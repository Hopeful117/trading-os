package com.hope.trading.trading_core.risk.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TradePlanRiskPort {
    Snapshot load(UUID tradePlanId, long version);
    void acknowledge(UUID tradePlanId, long version, UUID evaluationId, String decision, Instant evaluatedAt);

    record Snapshot(UUID tradePlanId, long tradePlanVersion, String status, Instant createdAt,
                    UUID contextId, long contextVersion, Instant contextSnapshotAt,
                    UUID ownerId, UUID tradingAccountId, String accountCurrency, BigDecimal leverage,
                    String instrument, String direction, BigDecimal entryPrice, BigDecimal stopPrice,
                    BigDecimal quantity, BigDecimal notional, BigDecimal expectedMonetaryRisk,
                    String sizingCurrency, String sourcePayload) { }
}
