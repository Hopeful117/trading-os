package com.hope.trading.market_intelligence.domain.momentum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MomentumAssessment(
        UUID marketId,
        String interval,
        Instant observedFrom,
        Instant observedTo,
        Instant cutoff,
        Status status,
        Direction direction,
        BigDecimal normalizedSlope,
        BigDecimal directionalPersistence,
        BigDecimal precedingMovement,
        BigDecimal recentMovement,
        Acceleration acceleration,
        String policyVersion,
        String algorithmVersion,
        String inputFingerprint,
        String resultFingerprint,
        String reason
) {
    public static final String ALGORITHM_VERSION = "ohlc-momentum-calculator/v1";

    public MomentumAssessment {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(direction, "direction");
        policyVersion = required(policyVersion, "policyVersion");
        algorithmVersion = required(algorithmVersion, "algorithmVersion");
        inputFingerprint = required(inputFingerprint, "inputFingerprint");
        resultFingerprint = required(resultFingerprint, "resultFingerprint");
        reason = required(reason, "reason");
        if (status == Status.AVAILABLE) {
            Objects.requireNonNull(marketId, "marketId");
            interval = required(interval, "interval");
            Objects.requireNonNull(observedFrom, "observedFrom");
            Objects.requireNonNull(observedTo, "observedTo");
            Objects.requireNonNull(cutoff, "cutoff");
            Objects.requireNonNull(normalizedSlope, "normalizedSlope");
            Objects.requireNonNull(directionalPersistence, "directionalPersistence");
            Objects.requireNonNull(precedingMovement, "precedingMovement");
            Objects.requireNonNull(recentMovement, "recentMovement");
            Objects.requireNonNull(acceleration, "acceleration");
        }
    }

    public static MomentumAssessment unavailable(
            UUID marketId, String interval, Instant cutoff, Status status,
            String policyVersion, String inputFingerprint, String reason,
            String resultFingerprint) {
        return new MomentumAssessment(marketId, interval, null, null, cutoff, status,
                Direction.UNKNOWN, null, null, null, null, Acceleration.UNKNOWN,
                policyVersion, ALGORITHM_VERSION, inputFingerprint, resultFingerprint, reason);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public enum Status { AVAILABLE, INSUFFICIENT_DATA, INVALID_INPUT }

    public enum Direction { UP, DOWN, NEUTRAL, UNKNOWN }

    public enum Acceleration { ACCELERATING, DECELERATING, STEADY, UNKNOWN }
}
