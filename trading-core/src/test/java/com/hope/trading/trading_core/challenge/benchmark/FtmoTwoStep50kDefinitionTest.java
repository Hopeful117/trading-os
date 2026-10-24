package com.hope.trading.trading_core.challenge.benchmark;

import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FtmoTwoStep50kDefinitionTest {
    private static final UUID PROFILE_ID = UUID.fromString(
            "0a10c7e2-9d1e-4f5a-b6c8-123456789090");
    private static final UUID DEFINITION_ID = UUID.fromString(
            "00890000-0000-4000-8000-000000000051");
    private static final String VERSION = "1.0.0";

    @Autowired private RiskPersistence riskPersistence;
    @Autowired private ChallengeDefinitionRepository definitions;

    @Test
    void flywayCatalogContainsVersionedFtmoTwoStepDefinition() {
        var profile = riskPersistence.profile(PROFILE_ID, VERSION).orElseThrow();
        var definition = definitions.findById(new ChallengeDefinitionKey(DEFINITION_ID, VERSION))
                .orElseThrow();

        assertThat(profile.policyId()).isEqualTo("FTMO_2STEP_50K_RISK");
        assertThat(profile.authority()).isEqualTo("PROP_FIRM");
        assertThat(profile.rules()).extracting(RiskPersistence.ProfileRule::ruleId)
                .containsExactlyInAnyOrder("MAX_POSITION_RISK", "MAX_EXPOSURE",
                        "DAILY_DRAWDOWN", "MAX_TOTAL_DRAWDOWN");
        assertThat(profile.rules()).filteredOn(rule -> "DAILY_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().satisfies(rule -> {
                    assertThat(rule.maximumRatio()).isEqualByComparingTo("0.05");
                    assertThat(rule.breachBoundary()).isEqualTo("STRICT");
                });
        assertThat(profile.rules()).filteredOn(rule -> "MAX_TOTAL_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().satisfies(rule -> {
                    assertThat(rule.maximumRatio()).isEqualByComparingTo("0.10");
                    assertThat(rule.breachBoundary()).isEqualTo("STRICT");
                });

        assertThat(definition.provider()).isEqualTo("FTMO");
        assertThat(definition.product()).isEqualTo("2_STEP");
        assertThat(definition.planCode()).isEqualTo("STANDARD");
        assertThat(definition.capitalCurrency()).isEqualTo("USD");
        assertThat(definition.startingCapital()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(definition.profitTargetRatio()).isEqualByComparingTo("0.10");
        assertThat(definition.progressionValueSource().name()).isEqualTo("BALANCE");
        assertThat(definition.riskTimeZone()).isEqualTo("Europe/Paris");
        assertThat(definition.riskDayResetTime()).hasToString("00:00");
        assertThat(definition.effectivePhases()).extracting(phase -> phase.phaseKey())
                .containsExactly("CHALLENGE", "VERIFICATION");
        assertThat(definition.effectivePhases()).extracting(phase -> phase.profitTargetRatio())
                .satisfiesExactly(
                        target -> assertThat(target).isEqualByComparingTo("0.10"),
                        target -> assertThat(target).isEqualByComparingTo("0.05"));
        assertThat(definition.effectivePhases()).extracting(phase -> phase.minimumTradingDays())
                .containsExactly(4, 4);
        assertThat(definition.effectivePhases()).extracting(phase -> phase.providerConfirmationRequired())
                .containsExactly(true, false);
        assertThat(definition.provenance()).contains("https://ftmo.com/en/trading-objectives/")
                .contains("BLOCKED_EXTERNAL");
    }
}
