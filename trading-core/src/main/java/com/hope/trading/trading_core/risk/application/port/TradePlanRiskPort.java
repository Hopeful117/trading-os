package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TradePlanRiskPort {
    Snapshot load(UUID tradePlanId, long version);
    void acknowledge(UUID tradePlanId, long version, UUID evaluationId, String decision, Instant evaluatedAt);

    record Snapshot(UUID tradePlanId, long tradePlanVersion, String status, Instant createdAt,
                    UUID contextId, long contextVersion, Instant contextSnapshotAt,
                    UUID ownerId, UUID tradingAccountId, String accountCurrency,
                    UUID riskBudgetSourceId, long riskBudgetSourceVersion,
                    UUID planningPreferencesId, long planningPreferencesVersion,
                    String instrument, String direction, EntryIntent entryIntent, BigDecimal stopPrice,
                    BigDecimal quantity, BigDecimal notional, BigDecimal expectedMonetaryRisk,
                    String sizingCurrency, String sourcePayload) { }
}
