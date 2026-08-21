package com.hope.trading.market_intelligence.strategy.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * A single typed, validated, deterministic strategy parameter.
 */
public record StrategyParameter(String name, ParameterType type, Object value) {

    public StrategyParameter {
        Objects.requireNonNull(name, "parameter name is required");
        Objects.requireNonNull(type, "parameter type is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be blank");
        }
        value = type.validate(value);
    }

    public enum ParameterType {
        DECIMAL {
            @Override
            BigDecimal coerce(Object raw) {
                return switch (raw) {
                    case BigDecimal decimal -> decimal;
                    case Number number -> BigDecimal.valueOf(number.doubleValue());
                    case String text -> new BigDecimal(text);
                    default -> throw new IllegalArgumentException("not a DECIMAL value: " + raw);
                };
            }
        },
        INTEGER {
            @Override
            Long coerce(Object raw) {
                return switch (raw) {
                    case Long longValue -> longValue;
                    case Number number -> number.longValue();
                    case String text -> Long.parseLong(text.trim());
                    default -> throw new IllegalArgumentException("not an INTEGER value: " + raw);
                };
            }
        },
        STRING {
            @Override
            String coerce(Object raw) {
                if (!(raw instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("not a STRING value: " + raw);
                }
                return text;
            }
        },
        DURATION {
            @Override
            Duration coerce(Object raw) {
                return switch (raw) {
                    case Duration duration -> duration;
                    case String text -> Duration.parse(text.trim());
                    default -> throw new IllegalArgumentException("not a DURATION value: " + raw);
                };
            }
        };

        abstract Object coerce(Object raw);

        Object validate(Object raw) {
            Objects.requireNonNull(raw, "parameter value is required");
            return coerce(raw);
        }
    }

    public BigDecimal decimalValue() {
        return (BigDecimal) value;
    }

    public long integerValue() {
        return (Long) value;
    }

    public String stringValue() {
        return (String) value;
    }

    public Duration durationValue() {
        return (Duration) value;
    }
}
