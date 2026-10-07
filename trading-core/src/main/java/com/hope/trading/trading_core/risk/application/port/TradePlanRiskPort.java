package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;

public interface TradePlanRiskPort {
    Snapshot load(UUID tradePlanId, long version);
    Snapshot prepareForExecution(UUID tradePlanId, long acceptedVersion, UUID evaluationId);
    Snapshot loadReady(UUID tradePlanId, long version);
    void acknowledge(UUID tradePlanId, long version, UUID evaluationId, String decision, Instant evaluatedAt);

    record Snapshot(UUID tradePlanId, long tradePlanVersion, String status, String origin, Instant createdAt,
                    UUID contextId, long contextVersion, Instant contextSnapshotAt,
                    UUID ownerId, UUID tradingAccountId, String accountCurrency,
                    UUID riskBudgetSourceId, long riskBudgetSourceVersion,
                    UUID planningPreferencesId, long planningPreferencesVersion,
                     String instrument, String direction, EntryIntent entryIntent, BigDecimal stopPrice,
                     BigDecimal takeProfit,
                     BigDecimal quantity, BigDecimal notional, BigDecimal expectedMonetaryRisk,
                     BigDecimal riskRewardRatio, String sizingCurrency, String sourcePayload,
                     List<TradePlanProvenance> provenance) {
        public Snapshot(UUID tradePlanId, long tradePlanVersion, String status, Instant createdAt,
                        UUID contextId, long contextVersion, Instant contextSnapshotAt,
                        UUID ownerId, UUID tradingAccountId, String accountCurrency,
                        UUID riskBudgetSourceId, long riskBudgetSourceVersion,
                        UUID planningPreferencesId, long planningPreferencesVersion,
                        String instrument, String direction, EntryIntent entryIntent, BigDecimal stopPrice,
                        BigDecimal takeProfit, BigDecimal quantity, BigDecimal notional,
                        BigDecimal expectedMonetaryRisk, String sizingCurrency, String sourcePayload) {
            this(tradePlanId, tradePlanVersion, status, "AUTOMATED", createdAt, contextId, contextVersion,
                    contextSnapshotAt, ownerId, tradingAccountId, accountCurrency, riskBudgetSourceId,
                    riskBudgetSourceVersion, planningPreferencesId, planningPreferencesVersion,
                    instrument, direction, entryIntent, stopPrice, takeProfit, quantity, notional,
                    expectedMonetaryRisk, null, sizingCurrency, sourcePayload, List.of());
        }

        public Snapshot {
            provenance = List.copyOf(provenance == null ? List.of() : provenance);
        }
    }
}
