package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.risk.api.RiskProfileCatalogResponse;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskProfileCatalogService {
    private final RiskPersistence persistence;
    private final RiskProfileValidator validator;

    public List<RiskProfileCatalogResponse> eligiblePlatformProfiles() {
        return persistence.profiles().stream()
                .filter(profile -> "PLATFORM".equals(profile.authority()))
                .filter(this::isEligible)
                .map(this::toResponse)
                .sorted(Comparator.comparing(RiskProfileCatalogResponse::policyId)
                        .thenComparing(RiskProfileCatalogResponse::semanticVersion)
                        .thenComparing(RiskProfileCatalogResponse::profileId))
                .toList();
    }

    private boolean isEligible(RiskPersistence.Profile profile) {
        try {
            validator.validate(profile, false);
            return true;
        } catch (RiskProfileValidationException invalid) {
            return false;
        }
    }

    private RiskProfileCatalogResponse toResponse(RiskPersistence.Profile profile) {
        List<RiskProfileCatalogResponse.RuleSummary> rules = profile.rules().stream()
                .map(rule -> new RiskProfileCatalogResponse.RuleSummary(
                        rule.ruleId(), rule.ruleVersion(), rule.category(), rule.severity(),
                        rule.priority(), rule.maximumRatio()))
                .sorted(Comparator.comparing(RiskProfileCatalogResponse.RuleSummary::ruleId))
                .toList();
        return new RiskProfileCatalogResponse(profile.id(), profile.semanticVersion(), profile.policyId(),
                profile.policyVersion(), profile.authority(), profile.createdAt(), profile.provenance(), rules);
    }
}
