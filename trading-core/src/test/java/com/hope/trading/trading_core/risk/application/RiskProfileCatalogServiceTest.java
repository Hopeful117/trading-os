package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskProfileCatalogServiceTest {
    private final RiskPersistence persistence = mock(RiskPersistence.class);
    private final RiskProfileCatalogService service =
            new RiskProfileCatalogService(persistence, new RiskProfileValidator());

    @Test
    void returnsOnlyValidPlatformProfiles() {
        RiskPersistence.Profile valid = profile("PLATFORM", "valid");
        RiskPersistence.Profile invalid = new RiskPersistence.Profile(
                UUID.randomUUID(), "1.0.0", "policy", "1.0.0", "PLATFORM", Instant.EPOCH,
                "provenance", null, null, List.of());
        RiskPersistence.Profile nonPlatform = profile("BROKER", "broker");
        when(persistence.profiles()).thenReturn(List.of(invalid, nonPlatform, valid));

        assertThat(service.eligiblePlatformProfiles())
                .extracting(response -> response.policyId())
                .containsExactly("valid");
    }

    private RiskPersistence.Profile profile(String authority, String policyId) {
        return new RiskPersistence.Profile(UUID.randomUUID(), "1.0.0", policyId, "1.0.0", authority,
                Instant.EPOCH, "provenance", null, null, List.of(
                rule("MAX_POSITION_RISK", "POSITION"),
                rule("MAX_EXPOSURE", "PORTFOLIO"),
                rule("DAILY_DRAWDOWN", "ACCOUNT")));
    }

    private RiskPersistence.ProfileRule rule(String id, String category) {
        return new RiskPersistence.ProfileRule(id, "1.0.0", category, "BLOCKING", 10,
                new BigDecimal("0.01"), "rule-provenance");
    }
}
