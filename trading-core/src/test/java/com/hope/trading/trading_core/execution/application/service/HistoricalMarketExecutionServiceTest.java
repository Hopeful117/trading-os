package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.port.HistoricalOrderBookPort;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalMarketExecutionServiceTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-17T14:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SEQUENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void mapsInsufficientHistoricalEvidenceToFailClosedExecution() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        when(port.replay(MARKET_ID, 25, CUTOFF)).thenReturn(new HistoricalOrderBookPort.Replay(
                HistoricalOrderBookPort.Status.UNAVAILABLE, MARKET_ID, 25, null, CUTOFF,
                null, null, null, null, "KRAKEN", "PF_XBTUSD", null, null, List.of(), List.of(),
                "No trusted snapshot"));

        var execution = new HistoricalMarketExecutionService(port).execute(MARKET_ID, intent("2"), CUTOFF);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.INSUFFICIENT_MARKET_EVIDENCE);
        assertThat(execution.filledQuantity()).isZero();
        assertThat(execution.averageExecutionPrice()).isNull();
    }

    @Test
    void preservesPartialVisibleDepthResult() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        when(port.replay(MARKET_ID, 25, CUTOFF)).thenReturn(replay(
                List.of(level("99", "5")), List.of(level("100", "1.7")), CUTOFF.minusSeconds(1)));

        var execution = new HistoricalMarketExecutionService(port).execute(MARKET_ID, intent("2"), CUTOFF);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.PARTIALLY_FILLED);
        assertThat(execution.filledQuantity()).isEqualByComparingTo("1.7");
        assertThat(execution.unfilledQuantity()).isEqualByComparingTo("0.3");
        assertThat(execution.executionFingerprint()).hasSize(64);
    }

    @Test
    void rejectsNonZeroLatencyCutoffUnderV1Policy() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        var decisionAt = CUTOFF.minusSeconds(1);
        var intent = new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY,
                StrategyIntent.OrderType.MARKET, new BigDecimal("1"), decisionAt);

        var execution = new HistoricalMarketExecutionService(port).execute(MARKET_ID, intent, CUTOFF);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.INVALID_INPUT);
        assertThat(execution.reason()).contains("zero-latency");
    }

    @Test
    void rejectsReplayForAnotherMarketOrDepth() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        when(port.replay(MARKET_ID, 25, CUTOFF)).thenReturn(replay(
                UUID.fromString("00000000-0000-0000-0000-000000000099"), 24,
                List.of(level("99", "5")), List.of(level("100", "1")), CUTOFF.minusSeconds(1),
                CUTOFF.minusSeconds(1), 0L, 10L));

        var execution = new HistoricalMarketExecutionService(port).execute(MARKET_ID, intent("1"), CUTOFF);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.INVALID_INPUT);
        assertThat(execution.reason()).contains("provenance");
    }

    @Test
    void rejectsAvailableReplayWithIncompleteTemporalProvenance() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        when(port.replay(MARKET_ID, 25, CUTOFF)).thenReturn(replay(
                MARKET_ID, 25, List.of(level("99", "5")), List.of(level("100", "1")),
                null, null, null, null));

        var execution = new HistoricalMarketExecutionService(port).execute(MARKET_ID, intent("1"), CUTOFF);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.INVALID_INPUT);
        assertThat(execution.reason()).contains("provenance");
    }

    @Test
    void unavailableFingerprintIncludesMarketAndInstrumentIdentity() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        var service = new HistoricalMarketExecutionService(port);

        var first = service.execute(MARKET_ID, new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY,
                StrategyIntent.OrderType.LIMIT, new BigDecimal("1"), CUTOFF), CUTOFF);
        var second = service.execute(UUID.fromString("00000000-0000-0000-0000-000000000099"),
                new StrategyIntent("ETH/USD", StrategyIntent.Side.BUY,
                        StrategyIntent.OrderType.LIMIT, new BigDecimal("1"), CUTOFF), CUTOFF);

        assertThat(first.executionFingerprint()).isNotEqualTo(second.executionFingerprint());
    }

    private HistoricalOrderBookPort.Replay replay(List<HistoricalOrderBookPort.Level> bids,
                                                   List<HistoricalOrderBookPort.Level> asks,
                                                   Instant knownAt) {
        return replay(MARKET_ID, 25, bids, asks, CUTOFF.minusSeconds(1), knownAt, 0L, 10L);
    }

    private HistoricalOrderBookPort.Replay replay(UUID marketId, int depth,
                                                   List<HistoricalOrderBookPort.Level> bids,
                                                   List<HistoricalOrderBookPort.Level> asks,
                                                   Instant latestOccurredAt, Instant latestKnownAt,
                                                   Long sourceStartOrdinal, Long sourceEndOrdinal) {
        return new HistoricalOrderBookPort.Replay(HistoricalOrderBookPort.Status.AVAILABLE, marketId, depth,
                SEQUENCE_ID, CUTOFF, latestOccurredAt, latestKnownAt, sourceStartOrdinal, sourceEndOrdinal,
                "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1", "book-fingerprint", bids, asks, null);
    }

    private StrategyIntent intent(String quantity) {
        return new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), CUTOFF);
    }

    private HistoricalOrderBookPort.Level level(String price, String quantity) {
        return new HistoricalOrderBookPort.Level(new BigDecimal(price), new BigDecimal(quantity));
    }
}
