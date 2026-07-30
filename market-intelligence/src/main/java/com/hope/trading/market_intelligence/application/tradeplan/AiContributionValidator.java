package com.hope.trading.market_intelligence.application.tradeplan;

import java.util.*;

public final class AiContributionValidator {
    private static final Set<ContributionType> ALLOWED = EnumSet.of(
            ContributionType.THESIS, ContributionType.CONFIRMATION,
            ContributionType.INVALIDATION, ContributionType.MANAGEMENT);

    public List<PlanningContribution> validate(
            AiPlanningProposal proposal, PlanningInput input) {
        if (!proposal.instrument().equalsIgnoreCase(input.instrument())
                || proposal.direction() != input.direction()) {
            throw new IllegalArgumentException("AI proposal instrument or direction mismatch");
        }
        if (!proposal.contributions().isEmpty() && proposal.sourceAnalysisIds().isEmpty()) {
            throw new IllegalArgumentException("AI rationale is not traceable");
        }
        return proposal.contributions().stream().map(item -> {
            if (!item.aiDerived() || !ALLOWED.contains(item.type())) {
                throw new IllegalArgumentException(
                        "AI cannot contribute trusted execution or risk parameters");
            }
            if (item.value() instanceof String text && text.isBlank()
                    || item.value() instanceof Collection<?> values && values.isEmpty()) {
                throw new IllegalArgumentException("Malformed AI contribution");
            }
            return item;
        }).toList();
    }
}
