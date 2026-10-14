package com.hope.trading.trading_core.market_data.valuation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface MarketValuationPort {
    Snapshot value(String reportingCurrency, Instant at, List<Instrument> instruments, List<Asset> assets);

    default Snapshot value(String reportingCurrency, Instant at, List<Instrument> instruments,
                           List<Asset> assets, FreshnessRequirement requirement) {
        return value(reportingCurrency, at, instruments, assets);
    }

    record Instrument(String id, String symbol, PriceUse priceUse) { }
    record Asset(String id, String currency) { }
    enum PriceUse { LAST, CONSERVATIVE_SELL, CONSERVATIVE_BUY }
    enum DecisionBoundary { PRE_TRADE, EXECUTION_TIME }
    enum AcquisitionMode { REUSE_OR_ACQUIRE, CONFIRM_OR_ACQUIRE }
    record FreshnessRequirement(DecisionBoundary boundary, AcquisitionMode acquisitionMode,
                                Duration maxObservationAge, Duration acquisitionDeadline) {
        public FreshnessRequirement {
            if (boundary == null || acquisitionMode == null || maxObservationAge == null
                    || maxObservationAge.isNegative() || acquisitionDeadline == null
                    || acquisitionDeadline.isNegative() || acquisitionDeadline.isZero()) {
                throw new IllegalArgumentException("Invalid market valuation freshness requirement");
            }
        }

        public static FreshnessRequirement t0() {
            return new FreshnessRequirement(DecisionBoundary.PRE_TRADE,
                    AcquisitionMode.REUSE_OR_ACQUIRE, Duration.ofMinutes(5), Duration.ofSeconds(18));
        }

        public static FreshnessRequirement t1() {
            return new FreshnessRequirement(DecisionBoundary.EXECUTION_TIME,
                    AcquisitionMode.CONFIRM_OR_ACQUIRE, Duration.ofSeconds(30), Duration.ofSeconds(18));
        }
    }
    record Snapshot(UUID snapshotId, long sourceVersion, String reportingCurrency,
                    Instant valuationTimestamp, Instant capturedAt, String policyVersion,
                    String maxObservationAge, boolean complete, List<Fact> facts, String sourcePayload,
                    List<AcquisitionOutcome> acquisitionOutcomes) {
        public Snapshot(UUID snapshotId, long sourceVersion, String reportingCurrency,
                        Instant valuationTimestamp, Instant capturedAt, String policyVersion,
                        String maxObservationAge, boolean complete, List<Fact> facts, String sourcePayload) {
            this(snapshotId, sourceVersion, reportingCurrency, valuationTimestamp, capturedAt, policyVersion,
                    maxObservationAge, complete, facts, sourcePayload, List.of());
        }
    }
    record AcquisitionOutcome(UUID marketId, String status) { }
    record Fact(String type, String id, UUID marketId, String asset, PriceUse priceUse,
                BigDecimal value, BigDecimal sourcePrice, BigDecimal quoteToReportingRate,
                String status, String sourceProvenance) { }
}
