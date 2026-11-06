package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record IntelligenceFinding(
        String findingId,
        String capabilityId,
        AnalysisOrigin origin,
        FindingType type,
        String title,
        String description,
        Map<String, BigDecimal> metrics,
        BigDecimal confidence,
        Set<ContextSectionType> contextSectionsUsed,
        Instant generatedAt,
        Set<UUID> evidenceReferences
) {
    public IntelligenceFinding {
        metrics = immutableOrEmpty(metrics);
        contextSectionsUsed = immutableOrEmpty(contextSectionsUsed);
        evidenceReferences = immutableOrEmpty(evidenceReferences);
    }

    private static <T> Set<T> immutableOrEmpty(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static <K, V> Map<K, V> immutableOrEmpty(Map<K, V> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public IntelligenceFinding(
            String findingId, String capabilityId, AnalysisOrigin origin, FindingType type,
            String title, String description, Map<String, BigDecimal> metrics,
            BigDecimal confidence, Set<ContextSectionType> contextSectionsUsed,
            Instant generatedAt) {
        this(findingId, capabilityId, origin, type, title, description, metrics,
                confidence, contextSectionsUsed, generatedAt, Set.of());
    }
}
