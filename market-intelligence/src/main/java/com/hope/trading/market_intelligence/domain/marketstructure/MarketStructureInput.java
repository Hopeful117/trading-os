package com.hope.trading.market_intelligence.domain.marketstructure;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarketStructureInput(
        UUID marketId, String provider, String symbol, String interval,
        List<MarketStructureCandle> candles, List<MarketStructureGap> gaps,
        Instant cutOffAt, String algorithmId, String ruleVersion,
        String policyId, String policyVersion, String parameterFingerprint,
        String inputFingerprint, MarketStructureEvidenceStatus evidenceStatus,
        List<String> findings, int pivotRadius, int minimumSeparationBars) {
    public MarketStructureInput {
        Objects.requireNonNull(marketId); provider = required(provider, "provider");
        symbol = required(symbol, "symbol"); interval = required(interval, "interval");
        candles = List.copyOf(candles); gaps = List.copyOf(gaps);
        Objects.requireNonNull(cutOffAt); algorithmId = required(algorithmId, "algorithmId");
        ruleVersion = required(ruleVersion, "ruleVersion"); policyId = required(policyId, "policyId");
        policyVersion = required(policyVersion, "policyVersion");
        parameterFingerprint = required(parameterFingerprint, "parameterFingerprint");
        inputFingerprint = required(inputFingerprint, "inputFingerprint");
        Objects.requireNonNull(evidenceStatus); findings = List.copyOf(findings);
        if (pivotRadius < 1 || minimumSeparationBars < 1) {
            throw new IllegalArgumentException("Structure parameters must be positive");
        }
    }

    public MarketStructureInput(List<MarketStructureCandle> candles, List<MarketStructureGap> gaps,
            Instant cutOffAt, int pivotRadius, int minimumSeparationBars) {
        this(new UUID(0, 1), "test", "TEST", "TEST", candles, gaps, cutOffAt,
                "CONFIRMED_SWING_V1", "market-structure-rules-v1", "TEST_POLICY", "1",
                "test-parameters", "test-input", MarketStructureEvidenceStatus.COMPLETE,
                List.of(), pivotRadius, minimumSeparationBars);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
