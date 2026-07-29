package com.hope.trading.market_intelligence.domain;

public record ContextSection(
        ContextSectionType type,
        ContextSectionStatus status,
        ContextSensitivity sensitivity,
        ContextPayload payload,
        ContextProvenance provenance,
        String message
) {
    public static ContextSection missing(ContextRequirement requirement, String message) {
        return new ContextSection(
                requirement.sectionType(),
                ContextSectionStatus.MISSING,
                requirement.sensitivity(),
                null,
                null,
                message
        );
    }

    public static ContextSection unavailable(ContextRequirement requirement, String message) {
        return new ContextSection(
                requirement.sectionType(),
                ContextSectionStatus.UNAVAILABLE,
                requirement.sensitivity(),
                null,
                null,
                message
        );
    }
}
