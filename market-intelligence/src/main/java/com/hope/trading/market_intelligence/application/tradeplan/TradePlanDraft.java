package com.hope.trading.market_intelligence.application.tradeplan;

import java.util.*;

final class TradePlanDraft {
    private final EnumMap<ContributionType, PlanningContribution> contributions =
            new EnumMap<>(ContributionType.class);
    private final List<PlanningConflict> conflicts = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    void apply(PlanningContribution incoming) {
        PlanningContribution existing = contributions.putIfAbsent(incoming.type(), incoming);
        if (existing != null && !existing.value().equals(incoming.value())) {
            conflicts.add(new PlanningConflict(
                    incoming.type(), existing.source(), incoming.source(),
                    existing.value(), incoming.value()));
        }
    }
    Optional<PlanningContribution> contribution(ContributionType type) {
        return Optional.ofNullable(contributions.get(type));
    }
    List<PlanningConflict> conflicts() { return List.copyOf(conflicts); }
    void warning(String warning) { warnings.add(warning); }
    List<String> warnings() { return List.copyOf(warnings); }
}
