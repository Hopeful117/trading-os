package com.hope.trading.market_intelligence.domain;

public record ContextRequirement(
        ContextSectionType sectionType,
        boolean required,
        ContextSensitivity sensitivity
) {
    public static ContextRequirement requiredPublic(ContextSectionType type) {
        return new ContextRequirement(type, true, ContextSensitivity.PUBLIC);
    }

    public static ContextRequirement optionalPublic(ContextSectionType type) {
        return new ContextRequirement(type, false, ContextSensitivity.PUBLIC);
    }
}
