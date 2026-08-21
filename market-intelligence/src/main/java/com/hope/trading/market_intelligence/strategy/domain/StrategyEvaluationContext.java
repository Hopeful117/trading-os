package com.hope.trading.market_intelligence.strategy.domain;

import java.math.BigDecimal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/**
 * Semantic evaluation context supplied to a StrategyEvaluator.
 *
 * <p>Deliberately infrastructure-free: it exposes WHAT a strategy needs, never
 * HOW the values were produced. The same shape is produced by live scanning
 * and future historical Backtest providers (ADR-034).</p>
 */
public final class StrategyEvaluationContext {


    private final UUID marketId;
    private final String instrument;
    private final StrategyApplicability.Timeframe timeframe;
    private final Instant evaluatedAt;
    private final Map<RequiredSemanticInput, SemanticValue> inputs;
    private final String digest;

    private StrategyEvaluationContext(
            UUID marketId,
            String instrument,
            StrategyApplicability.Timeframe timeframe,
            Instant evaluatedAt,
            Map<RequiredSemanticInput, SemanticValue> inputs
    ) {
        this.marketId = Objects.requireNonNull(marketId, "marketId is required");
        this.instrument = requireText(instrument, "instrument");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe is required");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        this.inputs = Map.copyOf(inputs);
        this.digest = computeDigest();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID marketId() { return marketId; }

    public String instrument() { return instrument; }

    public StrategyApplicability.Timeframe timeframe() { return timeframe; }

    /**
     * Authoritative evaluation time. Evaluators must use this value and must
     * never read wall-clock time.
     */
    public Instant evaluatedAt() { return evaluatedAt; }

    public boolean has(RequiredSemanticInput input) {
        return inputs.containsKey(input);
    }

    public SemanticValue get(RequiredSemanticInput input) {
        SemanticValue value = inputs.get(input);
        if (value == null) {
            throw new MissingSemanticInputException(input);
        }
        return value;
    }

    /** Deterministic content digest for provenance/replay. */
    public String digest() {
        return digest;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StrategyEvaluationContext context
                && marketId.equals(context.marketId)
                && instrument.equals(context.instrument)
                && timeframe == context.timeframe
                && evaluatedAt.equals(context.evaluatedAt)
                && inputs.equals(context.inputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId, instrument, timeframe, evaluatedAt, inputs);
    }

    private String computeDigest() {
        StringBuilder canonical = new StringBuilder()
                .append("market=").append(marketId).append(';')
                .append("instrument=").append(instrument).append(';')
                .append("timeframe=").append(timeframe).append(';')
                .append("evaluatedAt=").append(evaluatedAt);
        List<Map.Entry<RequiredSemanticInput, SemanticValue>> ordered = new ArrayList<>(inputs.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        for (Map.Entry<RequiredSemanticInput, SemanticValue> entry : ordered) {
            canonical.append(';')
                    .append(entry.getKey().toString()).append('=')
                    .append(canonicalValue(entry.getValue()));
        }
        byte[] hash = sha256(canonical.toString());
        return hex(hash);
    }

    private static String canonicalValue(SemanticValue value) {
        return switch (value.type()) {
            case DECIMAL -> value.decimalValue().setScale(12, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            case INTEGER -> Long.toString(value.integerValue());
            case STRING -> value.stringValue();
            case INSTANT -> value.instantValue().toString();
            case DURATION -> value.durationValue().toString();
        };
    }

    private static byte[] sha256(String content) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            hex.append(Character.forDigit((item >> 4) & 0xF, 16))
                    .append(Character.forDigit(item & 0xF, 16));
        }
        return hex.toString();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /** Typed semantic value readable by evaluators without source knowledge. */
    public record SemanticValue(Type type, Object raw) {

        public enum Type { DECIMAL, INTEGER, STRING, INSTANT, DURATION }

        public static SemanticValue decimal(BigDecimal value) {
            Objects.requireNonNull(value, "value is required");
            return new SemanticValue(Type.DECIMAL, value);
        }

        public static SemanticValue integer(long value) {
            return new SemanticValue(Type.INTEGER, value);
        }

        public static SemanticValue string(String value) {
            Objects.requireNonNull(value, "value is required");
            return new SemanticValue(Type.STRING, value);
        }

        public static SemanticValue instant(Instant value) {
            Objects.requireNonNull(value, "value is required");
            return new SemanticValue(Type.INSTANT, value);
        }

        public static SemanticValue duration(Duration value) {
            Objects.requireNonNull(value, "value is required");
            return new SemanticValue(Type.DURATION, value);
        }

        public BigDecimal decimalValue() { return requireType(Type.DECIMAL); }

        public long integerValue() { return requireType(Type.INTEGER); }

        public String stringValue() { return requireType(Type.STRING); }

        public Instant instantValue() { return requireType(Type.INSTANT); }

        public Duration durationValue() { return requireType(Type.DURATION); }

        private <T> T requireType(Type expected) {
            if (type != expected) {
                throw new IllegalStateException("semantic value is " + type + ", not " + expected);
            }
            @SuppressWarnings("unchecked")
            T result = (T) raw;
            return result;
        }
    }

    public static final class Builder {
        private UUID marketId;
        private String instrument;
        private StrategyApplicability.Timeframe timeframe;
        private Instant evaluatedAt;
        private final Map<RequiredSemanticInput, SemanticValue> inputs = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder marketId(UUID marketId) {
            this.marketId = marketId;
            return this;
        }

        public Builder instrument(String instrument) {
            this.instrument = instrument;
            return this;
        }

        public Builder timeframe(StrategyApplicability.Timeframe timeframe) {
            this.timeframe = timeframe;
            return this;
        }

        public Builder evaluatedAt(Instant evaluatedAt) {
            this.evaluatedAt = evaluatedAt;
            return this;
        }

        public Builder input(RequiredSemanticInput input, SemanticValue value) {
            Objects.requireNonNull(input, "input is required");
            Objects.requireNonNull(value, "value is required");
            inputs.put(input, value);
            return this;
        }

        public StrategyEvaluationContext build() {
            return new StrategyEvaluationContext(marketId, instrument, timeframe,
                    evaluatedAt, inputs);
        }
    }
}
