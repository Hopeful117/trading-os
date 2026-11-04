package com.hope.trading.trading_core.risk.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RiskFactsProviderEquityContractTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void rejectsNumericEquityThatDoesNotMatchTheStructuredFact() {
        var account = new RiskFactsProvider.Account("USD", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, null, reportedEquity(new BigDecimal("9")));

        assertThat(RiskFactsProvider.equityAdmissible(account, strictRequirements())).isFalse();
    }

    @Test
    void rejectsLegacyNumericEquityWithoutProvenanceForStrictRequirements() {
        var account = new RiskFactsProvider.Account("USD", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, null);

        assertThat(RiskFactsProvider.equityAdmissible(account, strictRequirements())).isFalse();
    }

    @Test
    void acceptsMatchingBrokerReportedEquityForStrictRequirements() {
        var account = new RiskFactsProvider.Account("USD", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, null, reportedEquity(BigDecimal.TEN));

        assertThat(RiskFactsProvider.equityAdmissible(account, strictRequirements())).isTrue();
    }

    private static RiskFactsProvider.EquityFact reportedEquity(BigDecimal value) {
        return new RiskFactsProvider.EquityFact(value, "USD",
                RiskFactRequirements.EquityProvenance.BROKER_REPORTED, "AVAILABLE",
                OBSERVED_AT, OBSERVED_AT, true, true, true, "BROKER_ACCOUNT", "ACCOUNT_EQUITY", java.util.List.of());
    }

    private static RiskFactRequirements strictRequirements() {
        return new RiskFactRequirements(true, true, false, false, false, false, false,
                Set.of(RiskFactRequirements.EquityProvenance.BROKER_REPORTED), true);
    }
}
