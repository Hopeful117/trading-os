package com.hope.trading.market_intelligence.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class IntelligenceContext {
    private final Map<ContextSectionType, ContextSection> sections;

    public IntelligenceContext(Map<ContextSectionType, ContextSection> sections) {
        Map<ContextSectionType, ContextSection> copy =
                new EnumMap<>(ContextSectionType.class);
        copy.putAll(sections);
        this.sections = Map.copyOf(copy);
    }

    public Optional<ContextSection> section(ContextSectionType type) {
        return Optional.ofNullable(sections.get(type));
    }

    public Map<ContextSectionType, ContextSection> sections() {
        return sections;
    }

    public IntelligenceContext select(Set<ContextSectionType> allowedSections) {
        Map<ContextSectionType, ContextSection> selected =
                new EnumMap<>(ContextSectionType.class);
        allowedSections.forEach(type -> {
            ContextSection section = sections.get(type);
            if (section != null) {
                selected.put(type, section);
            }
        });
        return new IntelligenceContext(selected);
    }
}
