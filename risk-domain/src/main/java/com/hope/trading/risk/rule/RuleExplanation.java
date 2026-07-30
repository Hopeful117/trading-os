package com.hope.trading.risk.rule;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record RuleExplanation(String code, Map<String, BigDecimal> values) {
    public RuleExplanation {
        code = Objects.requireNonNull(code).trim();
        values = Map.copyOf(values);
        if (code.isEmpty()) throw new IllegalArgumentException("explanation code required");
    }
}
