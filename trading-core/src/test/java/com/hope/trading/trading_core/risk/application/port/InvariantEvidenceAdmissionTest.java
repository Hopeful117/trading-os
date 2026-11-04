package com.hope.trading.trading_core.risk.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.risk.domain.RiskTypes.ProtectionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvariantEvidenceAdmissionTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void admitsPositionRiskIndependentlyFromUnavailableEquity() {
        String positionRule = "MAX_POSITION_RISK";
        String exposureRule = "MAX_EXPOSURE";
        var requirements = new RiskFactRequirements(true, true, true, false, false, false, false,
                java.util.Set.of(RiskFactRequirements.EquityProvenance.BROKER_REPORTED), true,
                Map.of(positionRule, new RiskFactRequirements.InvariantRequirements(
                                java.util.Set.of(RiskFactRequirements.Fact.BALANCE,
                                        RiskFactRequirements.Fact.POSITIONS,
                                        RiskFactRequirements.Fact.PROTECTIVE_STOPS), true),
                        exposureRule, new RiskFactRequirements.InvariantRequirements(
                                java.util.Set.of(RiskFactRequirements.Fact.EQUITY,
                                        RiskFactRequirements.Fact.POSITIONS), true)));
        var position = new RiskFactsProvider.Position(UUID.randomUUID(), "position-1", "BROKER",
                "EURUSD", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, List.of(new RiskFactsProvider.Stop("stop-1", "BROKER",
                        BigDecimal.ONE, new BigDecimal("0.9"))), ProtectionStatus.PROTECTED);
        var snapshot = new RiskFactsProvider.Snapshot(UUID.randomUUID(), 1, OBSERVED_AT, false,
                List.of("EQUITY_UNAVAILABLE"), Map.of("USD", new BigDecimal("10000")),
                new RiskFactsProvider.Account("USD", new BigDecimal("10000"), null,
                        BigDecimal.ZERO, null, RiskFactsProvider.EquityFact.unavailable("EQUITY_UNAVAILABLE")),
                List.of(position), List.of(), List.of(), "{}",
                RiskFactsProvider.DealAvailability.AVAILABLE_EMPTY);

        assertThat(InvariantEvidenceAdmission.resolve(snapshot, requirements, null, false))
                .containsEntry(exposureRule, "EQUITY_UNAVAILABLE")
                .doesNotContainKey(positionRule);
    }

    @Test
    void rejectsGlobalObservationFailureButAllowsScopedEquityFailure() {
        UUID sourceId = UUID.randomUUID();
        var incompleteObservation = snapshot(sourceId, false, "OBSERVATION_INCOMPLETE");
        var unavailableEquity = snapshot(sourceId, false, "EQUITY_UNAVAILABLE");
        var scopedIncompleteObservation = new RiskFactsProvider.Snapshot(
                sourceId, 1, OBSERVED_AT, false,
                List.of("EQUITY_UNAVAILABLE", "OBSERVATION_INCOMPLETE"),
                unavailableEquity.assetBalances(), unavailableEquity.account(), unavailableEquity.positions(),
                unavailableEquity.closedTrades(), unavailableEquity.ledgerEntries(), unavailableEquity.sourcePayload(),
                unavailableEquity.dealAvailability());

        assertThat(InvariantEvidenceAdmission.structuralFailure(incompleteObservation, sourceId))
                .isEqualTo("OBSERVATION_INCOMPLETE");
        assertThat(InvariantEvidenceAdmission.structuralFailure(unavailableEquity, sourceId))
                .isNull();
        assertThat(InvariantEvidenceAdmission.structuralFailure(scopedIncompleteObservation, sourceId))
                .isNull();
        assertThat(InvariantEvidenceAdmission.structuralFailure(
                snapshot(sourceId, false, "POSITIONS_UNAVAILABLE"), sourceId)).isNull();
    }

    @Test
    void rejectsSourceIdentityAndCurrencyShapeFailuresBeforeInvariantAdmission() {
        UUID sourceId = UUID.randomUUID();
        var snapshot = snapshot(sourceId, true, null);

        assertThat(InvariantEvidenceAdmission.structuralFailure(snapshot, UUID.randomUUID()))
                .isEqualTo("BROKER_RISK_FACTS_IDENTITY_MISMATCH");
        assertThat(InvariantEvidenceAdmission.structuralFailure(new RiskFactsProvider.Snapshot(
                sourceId, 1, OBSERVED_AT, true, List.of(), Map.of("", BigDecimal.ONE),
                snapshot.account(), List.of(), List.of(), List.of(), "{}",
                RiskFactsProvider.DealAvailability.AVAILABLE_EMPTY), sourceId))
                .isEqualTo("BROKER_RISK_FACTS_CURRENCY_OR_BALANCE_INVALID");
    }

    @Test
    void sameResolverProducesSameAdmissionForT0AndT1() {
        var requirements = new RiskFactRequirements(true, true, false, false, false, false, false,
                java.util.Set.of(RiskFactRequirements.EquityProvenance.BROKER_REPORTED), true,
                Map.of("MAX_EXPOSURE", new RiskFactRequirements.InvariantRequirements(
                        java.util.Set.of(RiskFactRequirements.Fact.EQUITY), true)));
        var t0Snapshot = snapshot(UUID.randomUUID(), false, "EQUITY_UNAVAILABLE");
        var t1Snapshot = snapshot(UUID.randomUUID(), false, "EQUITY_UNAVAILABLE");

        assertThat(InvariantEvidenceAdmission.resolve(t0Snapshot, requirements, null, false))
                .isEqualTo(InvariantEvidenceAdmission.resolve(t1Snapshot, requirements, null, false));
    }

    private RiskFactsProvider.Snapshot snapshot(UUID sourceId, boolean complete, String reason) {
        return new RiskFactsProvider.Snapshot(sourceId, 1, OBSERVED_AT, complete,
                reason == null ? List.of() : List.of(reason), Map.of("USD", new BigDecimal("10000")),
                new RiskFactsProvider.Account("USD", new BigDecimal("10000"), null,
                        BigDecimal.ZERO, null, RiskFactsProvider.EquityFact.unavailable("EQUITY_UNAVAILABLE")),
                List.of(), List.of(), List.of(), "{}",
                RiskFactsProvider.DealAvailability.AVAILABLE_EMPTY);
    }
}
