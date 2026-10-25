package com.hope.trading.market_intelligence.domain.tradeability;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisContradictionSeverity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TradeabilityAssessment(
        UUID marketId,
        String instrument,
        Instant cutoff,
        Instant observedAt,
        List<Evidence> evidence,
        String policyVersion,
        String resultFingerprint
) {
    public TradeabilityAssessment {
        Objects.requireNonNull(marketId, "marketId");
        instrument = required(instrument, "instrument");
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(observedAt, "observedAt");
        evidence = List.copyOf(evidence);
        policyVersion = required(policyVersion, "policyVersion");
        resultFingerprint = required(resultFingerprint, "resultFingerprint");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public record Evidence(
            Role role,
            MarketThesisAvailability availability,
            MarketThesisContradictionSeverity severity,
            String code,
            String source,
            String timeframe,
            String explanation,
            Instant observedAt,
            Instant sourceCutoff,
            String provider,
            String instrument,
            String sourceFingerprint,
            boolean required
    ) {
        public Evidence {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(availability, "availability");
            code = required(code, "code");
            source = required(source, "source");
            explanation = required(explanation, "explanation");
            Objects.requireNonNull(observedAt, "observedAt");
            if (role == Role.BLOCKING || role == Role.WEAKENING) {
                Objects.requireNonNull(severity, "severity");
            }
            if (role != Role.BLOCKING && role != Role.WEAKENING && severity != null) {
                throw new IllegalArgumentException("Only contradictions have severity");
            }
        }

        private static String required(String value, String field) {
            Objects.requireNonNull(value, field);
            if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            return value.trim();
        }
    }

    public enum Role {
        SUPPORTING,
        WEAKENING,
        BLOCKING,
        MISSING,
        INFORMATIONAL
    }
}
