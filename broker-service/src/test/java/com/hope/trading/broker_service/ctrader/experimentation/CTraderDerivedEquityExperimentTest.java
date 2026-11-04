package com.hope.trading.broker_service.ctrader.experimentation;

import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CTraderDerivedEquityExperimentTest {
    private static final Instant START = Instant.parse("2026-10-23T12:00:00Z");
    private static final Instant NOW = START.plusSeconds(30);

    @Test
    void calculatesCandidateFromBalanceAndNetPnlWithoutChangingPrecision() {
        var observation = observation(new BigDecimal("1000.005"),
                List.of(position("position-1"), position("position-2")),
                List.of(pnl("position-1", "12.345"), pnl("position-2", "-2.300")), 7L);

        var result = CTraderDerivedEquityExperiment.evaluate(observation, NOW,
                Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.candidateEquity()).isEqualByComparingTo("1010.050");
        assertThat(result.totalNetUnrealizedPnl()).isEqualByComparingTo("10.045");
        assertThat(result.consistencyStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.CONSISTENT_WITH_OBSERVATIONS);
        assertThat(result.validationStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.DerivedEquityValidationStatus.UNVERIFIED);
        assertThat(result.diagnostics())
                .contains("SWAP_COMMISSION_AND_MIRRORING_COMMISSION_NOT_INCLUDED_UNVERIFIED");
    }

    @Test
    void acceptsAnEmptyCompletePositionSet() {
        var result = CTraderDerivedEquityExperiment.evaluate(observation(new BigDecimal("1000"),
                        List.of(), List.of(), 7L), NOW, Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.candidateEquity()).isEqualByComparingTo("1000");
        assertThat(result.positionReferences()).isEmpty();
    }

    @Test
    void rejectsMissingPnlIdentityCoverage() {
        var result = CTraderDerivedEquityExperiment.evaluate(observation(new BigDecimal("1000"),
                List.of(position("position-1")), List.of(), 7L), NOW,
                Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.candidateEquity()).isNull();
        assertThat(result.consistencyStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOHERENT);
    }

    @Test
    void reportsMissingBalanceAndValuationAssetWithoutThrowing() {
        var observation = observation(new BigDecimal("1000"), List.of(), List.of(), 7L);

        var result = CTraderDerivedEquityExperiment.evaluate(
                new CTraderDerivedEquityExperiment.Input("9001", null, null, observation), NOW,
                Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.candidateEquity()).isNull();
        assertThat(result.consistencyStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOMPLETE);
        assertThat(result.diagnostics()).containsExactly("VALUATION_ASSET_UNAVAILABLE", "BALANCE_UNAVAILABLE");
    }

    @Test
    void rejectsDuplicatePnlIdentities() {
        var result = CTraderDerivedEquityExperiment.evaluate(observation(new BigDecimal("1000"),
                List.of(position("position-1")), List.of(pnl("position-1", "1"), pnl("position-1", "2")), 7L),
                NOW, Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.candidateEquity()).isNull();
        assertThat(result.diagnostics()).contains("DUPLICATE_PNL_ID:position-1");
    }

    @Test
    void rejectsStaleObservation() {
        var result = CTraderDerivedEquityExperiment.evaluate(observationAt(START,
                        new BigDecimal("1000"), List.of(), List.of(), 7L), NOW,
                Duration.ofSeconds(10), Duration.ofMinutes(1));

        assertThat(result.consistencyStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.STALE);
        assertThat(result.candidateEquity()).isNull();
    }

    @Test
    void detectsChangedBalanceVersionBetweenReads() {
        var first = observation(new BigDecimal("1000"), List.of(), List.of(), 7L);
        var second = observation(new BigDecimal("1000"), List.of(), List.of(), 8L);

        var result = CTraderDerivedEquityExperiment.compare(first, second);

        assertThat(result.status()).isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOHERENT);
        assertThat(result.diagnostics()).contains("BALANCE_VERSION_CHANGED");
    }

    @Test
    void rejectsInvertedObservationTimestamps() {
        var observation = new CTraderReadOnlyObservation("9001", "USD", new BigDecimal("1000"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of(), NOW, START, START,
                true, List.of(), CTraderReadOnlyObservation.AccountFacts.empty(),
                CTraderReadOnlyObservation.CollectionState.available(List.of()),
                CTraderReadOnlyObservation.CollectionState.available(List.of()),
                CTraderReadOnlyObservation.CollectionState.available(List.of()), true,
                CTraderReadOnlyObservation.CollectionState.available(List.of()));

        var result = CTraderDerivedEquityExperiment.evaluate(observation, NOW,
                Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThat(result.consistencyStatus())
                .isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.UNVERIFIED);
        assertThat(result.diagnostics()).contains("OBSERVATION_TIMESTAMP_ORDER_INVALID");
    }

    @Test
    void doesNotClaimAtomicityWhenBalanceVersionIsAbsent() {
        var first = observation(new BigDecimal("1000"), List.of(), List.of(), null);
        var second = observation(new BigDecimal("1000"), List.of(), List.of(), null);

        var result = CTraderDerivedEquityExperiment.compare(first, second);

        assertThat(result.status()).isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.UNVERIFIED);
        assertThat(result.diagnostics()).contains("BALANCE_VERSION_UNAVAILABLE");
    }

    @Test
    void rejectsMissingPnlValuesDuringComparison() {
        var missingPnl = new CTraderReadOnlyObservation.PositionPnl("position-1",
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent());
        var first = observation(new BigDecimal("1000"), List.of(position("position-1")),
                List.of(missingPnl), 7L);
        var second = observation(new BigDecimal("1000"), List.of(position("position-1")),
                List.of(missingPnl), 7L);

        var result = CTraderDerivedEquityExperiment.compare(first, second);

        assertThat(result.status()).isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOMPLETE);
        assertThat(result.diagnostics()).contains("FIRST_NET_UNREALIZED_PNL_UNAVAILABLE");
    }

    @Test
    void rejectsDuplicatePositionIdentitiesDuringComparison() {
        var duplicate = List.of(position("position-1"), position("position-1"));
        var pnls = List.of(pnl("position-1", "1"));
        var first = observation(new BigDecimal("1000"), duplicate, pnls, 7L);

        var result = CTraderDerivedEquityExperiment.compare(first, first);

        assertThat(result.status()).isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOHERENT);
    }

    @Test
    void detectsChangedPositionFactsBetweenReads() {
        var first = observation(new BigDecimal("1000"), List.of(position("position-1")),
                List.of(pnl("position-1", "1")), 7L);
        var changed = new CTraderReadOnlyObservation.Position("EUR/USD", new BigDecimal("2"),
                new BigDecimal("1.1"), START, "position-1");
        var second = observation(new BigDecimal("1000"), List.of(changed),
                List.of(pnl("position-1", "1")), 7L);

        var result = CTraderDerivedEquityExperiment.compare(first, second);

        assertThat(result.status()).isEqualTo(CTraderDerivedEquityExperiment.ConsistencyStatus.INCOHERENT);
    }

    private static CTraderReadOnlyObservation observation(BigDecimal balance,
                                                           List<CTraderReadOnlyObservation.Position> positions,
                                                           List<CTraderReadOnlyObservation.PositionPnl> pnls,
                                                           Long balanceVersion) {
        return observationAt(NOW, balance, positions, pnls, balanceVersion);
    }

    private static CTraderReadOnlyObservation observationAt(Instant observedAt, BigDecimal balance,
                                                             List<CTraderReadOnlyObservation.Position> positions,
                                                             List<CTraderReadOnlyObservation.PositionPnl> pnls,
                                                             Long balanceVersion) {
        var accountFacts = new CTraderReadOnlyObservation.AccountFacts(
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.absent(),
                balanceVersion == null ? CTraderReadOnlyObservation.ProviderField.absent()
                        : CTraderReadOnlyObservation.ProviderField.present(balanceVersion));
        return new CTraderReadOnlyObservation("9001", "USD", balance, null, BigDecimal.ZERO,
                balance, positions, List.of(), List.of(), START, observedAt, observedAt,
                true, List.of(), accountFacts,
                CTraderReadOnlyObservation.CollectionState.available(positions),
                CTraderReadOnlyObservation.CollectionState.available(List.of()),
                CTraderReadOnlyObservation.CollectionState.available(pnls), true,
                CTraderReadOnlyObservation.CollectionState.available(List.of()));
    }

    private static CTraderReadOnlyObservation.Position position(String reference) {
        return new CTraderReadOnlyObservation.Position("EUR/USD", BigDecimal.ONE,
                new BigDecimal("1.1"), START, reference);
    }

    private static CTraderReadOnlyObservation.PositionPnl pnl(String reference, String value) {
        return new CTraderReadOnlyObservation.PositionPnl(reference,
                CTraderReadOnlyObservation.ProviderField.absent(),
                CTraderReadOnlyObservation.ProviderField.present(new BigDecimal(value)),
                CTraderReadOnlyObservation.ProviderField.absent());
    }
}
