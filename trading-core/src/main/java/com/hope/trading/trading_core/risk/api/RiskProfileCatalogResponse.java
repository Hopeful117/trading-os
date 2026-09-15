package com.hope.trading.trading_core.risk.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskProfileCatalogResponse(
        UUID profileId,
        String semanticVersion,
        String policyId,
        String policyVersion,
        String authority,
        Instant createdAt,
        String provenance,
        List<RuleSummary> rules
) {
    public record RuleSummary(
            String ruleId,
            String ruleVersion,
            String category,
            String severity,
            int priority,
            BigDecimal maximumRatio
    ) { }
}
