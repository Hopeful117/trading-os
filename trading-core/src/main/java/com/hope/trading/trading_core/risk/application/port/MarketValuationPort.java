package com.hope.trading.trading_core.risk.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarketValuationPort {
    Snapshot value(String reportingCurrency, Instant at, List<Instrument> instruments, List<Asset> assets);

    record Instrument(String id, String symbol, PriceUse priceUse) { }
    record Asset(String id, String currency) { }
    enum PriceUse { LAST, CONSERVATIVE_SELL, CONSERVATIVE_BUY }
    record Snapshot(UUID snapshotId, long sourceVersion, String reportingCurrency,
                    Instant valuationTimestamp, Instant capturedAt, String policyVersion,
                    String maxObservationAge, boolean complete, List<Fact> facts, String sourcePayload) { }
    record Fact(String type, String id, UUID marketId, String asset, PriceUse priceUse,
                BigDecimal value, BigDecimal sourcePrice, BigDecimal quoteToReportingRate,
                String status, String sourceProvenance) { }
}
