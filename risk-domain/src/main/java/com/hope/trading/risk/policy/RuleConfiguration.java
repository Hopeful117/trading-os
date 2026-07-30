package com.hope.trading.risk.policy;

import com.hope.trading.risk.domain.RiskTypes.RuleCategory;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record RuleConfiguration(
        String ruleId, String ruleVersion, RuleCategory category, RuleSeverity severity,
        int priority, Map<String, Object> parameters
) {
    public RuleConfiguration {
        ruleId = required(ruleId); ruleVersion = required(ruleVersion);
        Objects.requireNonNull(category); Objects.requireNonNull(severity);
        parameters = Map.copyOf(parameters);
        if (parameters.values().stream().anyMatch(value ->
                !(value instanceof BigDecimal || value instanceof String
                        || value instanceof Boolean || value instanceof Long
                        || value instanceof Integer))) {
            throw new IllegalArgumentException(
                    "Rule parameters must use supported immutable value types");
        }
        if (priority < 0) throw new IllegalArgumentException("priority cannot be negative");
    }
    public BigDecimal requiredParameter(String name) {
        Object value = parameters.get(name);
        if (!(value instanceof BigDecimal decimal)) {
            throw new IllegalArgumentException(
                    "Missing or non-decimal rule parameter: " + name);
        }
        return decimal;
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("value is required");
        return result;
    }
}
