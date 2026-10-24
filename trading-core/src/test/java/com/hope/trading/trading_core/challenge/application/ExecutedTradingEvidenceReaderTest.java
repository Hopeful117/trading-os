package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ExecutedTradingEvidence;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutedTradingEvidenceReaderTest {
    private static final Instant START = Instant.parse("2026-10-23T00:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void countsDistinctRiskDaysFromProvenanceAndUsesChallengeTimezone() {
        UUID accountId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        ChallengeInstance challenge = challenge(accountId, "Europe/Paris", LocalTime.of(6, 0));
        TradeRepository trades = mock(TradeRepository.class);
        TradeExecutionProvenanceStore provenance = mock(TradeExecutionProvenanceStore.class);
        when(trades.findAllByAccount_AccountId(accountId)).thenReturn(List.of(trade(accountId, tradeId, START)));
        when(provenance.findAllByTradeIdsAndExecutedAtBetween(
                any(), any(), any())).thenReturn(List.of(
                provenance(tradeId, Instant.parse("2026-10-23T05:30:00Z")),
                provenance(tradeId, Instant.parse("2026-10-23T07:00:00Z")),
                provenance(tradeId, Instant.parse("2026-10-24T05:00:00Z"))));

        ExecutedTradingEvidence evidence = new ExecutedTradingEvidenceReader(
                trades, provenance, Clock.fixed(OBSERVED, ZoneOffset.UTC)).read(challenge);

        assertThat(evidence.status()).isEqualTo(ExecutedTradingEvidence.Status.COMPLETE);
        assertThat(evidence.qualifyingTradingDays()).containsExactlyInAnyOrder(
                LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24));
        assertThat(evidence.sourceReference()).startsWith("paper-trade-execution-provenance:");
    }

    @Test
    void rejectsMalformedProvenanceFailClosed() {
        UUID accountId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        ChallengeInstance challenge = challenge(accountId, "UTC", LocalTime.MIDNIGHT);
        TradeRepository trades = mock(TradeRepository.class);
        TradeExecutionProvenanceStore provenance = mock(TradeExecutionProvenanceStore.class);
        when(trades.findAllByAccount_AccountId(accountId)).thenReturn(List.of(trade(accountId, tradeId, START)));
        TradeExecutionProvenanceStore.Provenance malformed = provenance(tradeId, OBSERVED.minusSeconds(60));
        malformed = new TradeExecutionProvenanceStore.Provenance(malformed.id(), malformed.tradeId(),
                malformed.executionAttemptId(), malformed.brokerOrderId(), " ", malformed.executedAt(),
                malformed.executionMode(), malformed.executionPurpose());
        when(provenance.findAllByTradeIdsAndExecutedAtBetween(
                any(), any(), any())).thenReturn(List.of(malformed));

        ExecutedTradingEvidence evidence = new ExecutedTradingEvidenceReader(
                trades, provenance, Clock.fixed(OBSERVED, ZoneOffset.UTC)).read(challenge);

        assertThat(evidence.status()).isEqualTo(ExecutedTradingEvidence.Status.CONTRADICTORY);
        assertThat(evidence.qualifyingTradingDays()).isEmpty();
    }

    @Test
    void rejectsProvenanceForTradeWithoutOpenedAt() {
        UUID accountId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        ChallengeInstance challenge = challenge(accountId, "UTC", LocalTime.MIDNIGHT);
        TradeRepository trades = mock(TradeRepository.class);
        TradeExecutionProvenanceStore provenance = mock(TradeExecutionProvenanceStore.class);
        when(trades.findAllByAccount_AccountId(accountId)).thenReturn(List.of(trade(accountId, tradeId, null)));
        when(provenance.findAllByTradeIdsAndExecutedAtBetween(any(), any(), any()))
                .thenReturn(List.of(provenance(tradeId, OBSERVED.minusSeconds(60))));

        ExecutedTradingEvidence evidence = new ExecutedTradingEvidenceReader(
                trades, provenance, Clock.fixed(OBSERVED, ZoneOffset.UTC)).read(challenge);

        assertThat(evidence.status()).isEqualTo(ExecutedTradingEvidence.Status.CONTRADICTORY);
        assertThat(evidence.qualifyingTradingDays()).isEmpty();
    }

    @Test
    void missingChallengeRiskDayConfigurationIsNotUsableEvidence() {
        UUID accountId = UUID.randomUUID();
        ChallengeInstance challenge = challenge(accountId, null, null);
        ExecutedTradingEvidenceReader reader = new ExecutedTradingEvidenceReader(
                mock(TradeRepository.class), mock(TradeExecutionProvenanceStore.class),
                Clock.fixed(OBSERVED, ZoneOffset.UTC));

        assertThat(reader.read(challenge).status()).isEqualTo(ExecutedTradingEvidence.Status.CONTRADICTORY);
    }

    private ChallengeInstance challenge(UUID accountId, String zone, LocalTime reset) {
        UUID policyId = UUID.randomUUID();
        ChallengeDefinition definition = ChallengeDefinition.create(
                UUID.randomUUID(), "1.0.0", "FTMO", "PAPER", "2STEP_50K", "USD",
                new BigDecimal("50000"), new BigDecimal("0.10"), ProgressionValueSource.BALANCE,
                SettlementModel.INVENTORY, null, policyId, "1.0.0", "https://example.test",
                START, START, "test", START, List.of(), zone, reset);
        return ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), START);
    }

    private Trade trade(UUID accountId, UUID tradeId, Instant openedAt) {
        return Trade.builder().tradeId(tradeId).symbol("ETHUSD").type(TradeType.BUY)
                .entryPrice(BigDecimal.ONE).quantity(BigDecimal.ONE).openedAt(openedAt).tradeStatus(TradeStatus.OPEN)
                .build();
    }

    private TradeExecutionProvenanceStore.Provenance provenance(UUID tradeId, Instant executedAt) {
        return new TradeExecutionProvenanceStore.Provenance(UUID.randomUUID(), tradeId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID().toString(), executedAt, ExecutionMode.PAPER.name(), "ENTRY");
    }
}
