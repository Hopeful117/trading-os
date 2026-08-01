package com.hope.trading.trading_core.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskDayAndReconstructionTest {
    @Test
    void createsDstCorrectHalfOpenRiskDays() {
        RiskDay spring = RiskDay.containing(Instant.parse("2026-03-29T12:00:00Z"), "Europe/Paris");
        RiskDay autumn = RiskDay.containing(Instant.parse("2026-10-25T12:00:00Z"), "Europe/Paris");

        assertThat(Duration.between(spring.startsAt(), spring.endsAt())).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(autumn.startsAt(), autumn.endsAt())).isEqualTo(Duration.ofHours(25));
        assertThat(spring.contains(spring.startsAt())).isTrue();
        assertThat(spring.contains(spring.endsAt())).isFalse();
    }

    @Test
    void reconstructsStartFromCurrentBalancesAndCompleteLedgerNetMovements() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        RiskDay day = new RiskDay(java.time.LocalDate.parse("2026-08-01"), from, from.plus(Duration.ofDays(1)));
        var snapshot = new BrokerRiskFactsPort.Snapshot(UUID.randomUUID(), 7, from.plusSeconds(100), true,
                List.of(), Map.of("USD", new BigDecimal("1120")), null, List.of(), List.of(),
                List.of(new BrokerRiskFactsPort.LedgerEntry("ledger-1", "USD", "deposit",
                        new BigDecimal("125"), new BigDecimal("5"), new BigDecimal("1120"), from)), "{}");

        assertThat(TradePlanRiskEvaluationService.reconstructStartBalances(snapshot, day))
                .containsEntry("USD", new BigDecimal("1000"));
    }

    @Test
    void rejectsAuthoritativeRunningAndTerminalBalanceMismatch() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        RiskDay day = day(from);
        var entries = List.of(
                entry("L1", "10", "1", "1009", from.plusSeconds(1)),
                entry("L2", "5", "1", "9999", from.plusSeconds(2)));

        assertThatThrownBy(() -> TradePlanRiskEvaluationService.reconstructStartBalances(
                snapshot(from, "1013", entries), day))
                .hasMessage("LEDGER_RUNNING_BALANCE_MISMATCH");

        assertThatThrownBy(() -> TradePlanRiskEvaluationService.reconstructStartBalances(
                snapshot(from, "9999", List.of(entry("L1", "10", "1", "1009", from.plusSeconds(1)))), day))
                .hasMessage("LEDGER_TERMINAL_BALANCE_MISMATCH");
    }

    @Test
    void rejectsLedgerEntryAfterSnapshotObservation() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        var snapshot = snapshot(from, "1009", List.of(entry("L1", "10", "1", "1009", from.plusSeconds(101))));

        assertThatThrownBy(() -> TradePlanRiskEvaluationService.reconstructStartBalances(snapshot, day(from)))
                .hasMessage("LEDGER_INTERVAL_INCOMPLETE");
    }

    @Test
    void appliesEachFeeExactlyOnceWhenReconstructingMultipleEntries() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        var entries = List.of(
                entry("L1", "100", "2", "1098", from.plusSeconds(1)),
                entry("L2", "-40", "3", "1055", from.plusSeconds(2)));

        assertThat(TradePlanRiskEvaluationService.reconstructStartBalances(
                snapshot(from, "1055", entries), day(from)))
                .containsEntry("USD", new BigDecimal("1000"));
    }

    @Test
    void rejectsNegativeLedgerFee() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");

        assertThatThrownBy(() -> TradePlanRiskEvaluationService.reconstructStartBalances(
                snapshot(from, "1011", List.of(entry("L1", "10", "-1", "1011", from.plusSeconds(1)))), day(from)))
                .hasMessage("LEDGER_FEE_INVALID");
    }

    private RiskDay day(Instant from) {
        return new RiskDay(java.time.LocalDate.parse("2026-08-01"), from, from.plus(Duration.ofDays(1)));
    }

    private BrokerRiskFactsPort.Snapshot snapshot(Instant from, String current,
                                                   List<BrokerRiskFactsPort.LedgerEntry> entries) {
        return new BrokerRiskFactsPort.Snapshot(UUID.randomUUID(), 7, from.plusSeconds(100), true,
                List.of(), Map.of("USD", new BigDecimal(current)), null, List.of(), List.of(), entries, "{}");
    }

    private BrokerRiskFactsPort.LedgerEntry entry(String id, String amount, String fee, String balance, Instant at) {
        return new BrokerRiskFactsPort.LedgerEntry(id, "USD", "trade", new BigDecimal(amount),
                new BigDecimal(fee), new BigDecimal(balance), at);
    }
}
