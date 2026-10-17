package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.command.HistoricalEconomicEvaluationRequest;
import com.hope.trading.trading_core.execution.application.port.HistoricalOrderBookPort;
import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.CostEvidence;
import com.hope.trading.trading_core.execution.domain.economic.EconomicLifecycleEvaluator;
import com.hope.trading.trading_core.execution.domain.economic.EconomicProductType;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.ExplicitCost;
import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecution;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;
import com.hope.trading.trading_core.execution.domain.economic.VisibleDepthMarketExecutionCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalEconomicEvaluationServiceTest {
    private static final Instant ENTRY_CUTOFF = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant EXIT_CUTOFF = Instant.parse("2026-10-17T11:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void preservesPositiveExecutionAdjustedPnlWhenPerpetualCostsAreUnavailable() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, ENTRY_CUTOFF, "100"),
                filled(StrategyIntent.Side.BUY, "1", "100", ENTRY_CUTOFF));
        var exit = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, EXIT_CUTOFF, "110"),
                filled(StrategyIntent.Side.SELL, "1", "110", EXIT_CUTOFF));
        when(executionService.replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF)))
                .thenReturn(entry);
        when(executionService.replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.SELL, "1")), eq(EXIT_CUTOFF)))
                .thenReturn(exit);

        var result = service(executionService).evaluate(request(StrategyIntent.Side.BUY, "1", unavailableCosts()));

        assertThat(result.status()).isEqualTo(EconomicResultStatus.DEGRADED);
        assertThat(result.completeness()).isEqualTo(CostCompleteness.PARTIALLY_COSTED);
        assertThat(result.executionAdjustedPnl()).isEqualByComparingTo("10");
        assertThat(result.netPnl()).isNull();
        assertThat(result.closedQuantity()).isEqualByComparingTo("1");
        assertThat(result.residualExposure()).isZero();
    }

    @Test
    void usesActualPartialEntryExposureForPartialExit() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entryIntent = intent(StrategyIntent.Side.BUY, "2");
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, ENTRY_CUTOFF, "100"),
                new SimulatedExecution(StrategyIntent.Side.BUY, new BigDecimal("2"), new BigDecimal("1.7"),
                        new BigDecimal("100"), ENTRY_CUTOFF, SimulatedExecutionStatus.PARTIALLY_FILLED,
                        "visible-depth-market-execution-v1", "entry"));
        var exit = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, EXIT_CUTOFF, "110"),
                filled(StrategyIntent.Side.SELL, "1.2", "110", EXIT_CUTOFF));
        when(executionService.replay(eq(MARKET_ID), eq(entryIntent), eq(ENTRY_CUTOFF))).thenReturn(entry);
        when(executionService.replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.SELL, "1.7")), eq(EXIT_CUTOFF)))
                .thenReturn(exit);

        var result = service(executionService).evaluate(request(entryIntent, unavailableCosts()));

        assertThat(result.executionStatus()).isEqualTo(SimulatedExecutionStatus.PARTIALLY_FILLED);
        assertThat(result.closedQuantity()).isEqualByComparingTo("1.2");
        assertThat(result.residualExposure()).isEqualByComparingTo("0.5");
        assertThat(result.executionNotional()).isEqualByComparingTo("120");
        assertThat(result.executionAdjustedPnl()).isEqualByComparingTo("12");
        verify(executionService).replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.SELL, "1.7")), eq(EXIT_CUTOFF));
    }

    @Test
    void doesNotReplayExitWhenEntryHasNoFill() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, ENTRY_CUTOFF, "100"),
                new SimulatedExecution(StrategyIntent.Side.BUY, BigDecimal.ONE, BigDecimal.ZERO, null,
                        ENTRY_CUTOFF, SimulatedExecutionStatus.UNFILLED,
                        "visible-depth-market-execution-v1", "entry"));
        when(executionService.replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF)))
                .thenReturn(entry);

        var result = service(executionService).evaluate(request(StrategyIntent.Side.BUY, "1", unavailableCosts()));

        assertThat(result.status()).isEqualTo(EconomicResultStatus.EXECUTION_UNAVAILABLE);
        assertThat(result.closedQuantity()).isZero();
        assertThat(result.residualExposure()).isZero();
        verify(executionService, times(1)).replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF));
    }

    @Test
    void preservesExposureWhenExitReplayIsUnavailable() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, ENTRY_CUTOFF, "100"),
                filled(StrategyIntent.Side.BUY, "1", "100", ENTRY_CUTOFF));
        var exit = new HistoricalMarketExecutionService.HistoricalExecution(null,
                new SimulatedExecution(StrategyIntent.Side.SELL, BigDecimal.ONE, BigDecimal.ZERO, null,
                        EXIT_CUTOFF, SimulatedExecutionStatus.INSUFFICIENT_MARKET_EVIDENCE,
                        "visible-depth-market-execution-v1", "exit-unavailable"));
        when(executionService.replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF)))
                .thenReturn(entry);
        when(executionService.replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.SELL, "1")), eq(EXIT_CUTOFF)))
                .thenReturn(exit);

        var result = service(executionService).evaluate(request(StrategyIntent.Side.BUY, "1", unavailableCosts()));

        assertThat(result.status()).isEqualTo(EconomicResultStatus.EXECUTION_UNAVAILABLE);
        assertThat(result.closedQuantity()).isZero();
        assertThat(result.residualExposure()).isEqualByComparingTo("1");
        assertThat(result.netPnl()).isNull();
    }

    @Test
    void supportsShortDirectionWithFullyCostedSpotReference() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.SPOT, ENTRY_CUTOFF, "100"),
                filled(StrategyIntent.Side.SELL, "1", "100", ENTRY_CUTOFF));
        var exit = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.SPOT, EXIT_CUTOFF, "90"),
                filled(StrategyIntent.Side.BUY, "1", "90", EXIT_CUTOFF));
        when(executionService.replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.SELL, "1")), eq(ENTRY_CUTOFF)))
                .thenReturn(entry);
        when(executionService.replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.BUY, "1")), eq(EXIT_CUTOFF)))
                .thenReturn(exit);

        var result = service(executionService).evaluate(request(StrategyIntent.Side.SELL, "1", fullyCostedSpotCosts()));

        assertThat(result.status()).isEqualTo(EconomicResultStatus.AVAILABLE);
        assertThat(result.completeness()).isEqualTo(CostCompleteness.FULLY_COSTED);
        assertThat(result.executionAdjustedPnl()).isEqualByComparingTo("10");
        assertThat(result.netPnl()).isEqualByComparingTo("10");
    }

    @Test
    void rejectsCostEvidenceKnownAfterResearchCutoffBeforeReplay() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        CostEvidence future = new CostEvidence("future", "fixture", ENTRY_CUTOFF.minusSeconds(1),
                null, EXIT_CUTOFF.plusSeconds(1));
        TransactionCosts costs = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(BigDecimal.ZERO, future, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), BigDecimal.ZERO, "cost-v1");

        var result = service(executionService).evaluate(request(StrategyIntent.Side.BUY, "1", costs));

        assertThat(result.status()).isEqualTo(EconomicResultStatus.STALE);
        assertThat(result.calculationFingerprint()).hasSize(64);
        verify(executionService, times(0)).replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF));
    }

    @Test
    void repeatsIdenticalLifecycleWithTheSameEconomicFingerprint() {
        HistoricalMarketExecutionService executionService = mock(HistoricalMarketExecutionService.class);
        var entry = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, ENTRY_CUTOFF, "100"),
                filled(StrategyIntent.Side.BUY, "1", "100", ENTRY_CUTOFF));
        var exit = new HistoricalMarketExecutionService.HistoricalExecution(
                state(EconomicProductType.LINEAR_PERPETUAL, EXIT_CUTOFF, "110"),
                filled(StrategyIntent.Side.SELL, "1", "110", EXIT_CUTOFF));
        when(executionService.replay(eq(MARKET_ID), eq(intent(StrategyIntent.Side.BUY, "1")), eq(ENTRY_CUTOFF)))
                .thenReturn(entry);
        when(executionService.replay(eq(MARKET_ID), eq(exitIntent(StrategyIntent.Side.SELL, "1")), eq(EXIT_CUTOFF)))
                .thenReturn(exit);

        var service = service(executionService);
        var request = request(StrategyIntent.Side.BUY, "1", unavailableCosts());
        var first = service.evaluate(request);
        var second = service.evaluate(request);

        assertThat(second.calculationFingerprint()).isEqualTo(first.calculationFingerprint());
        assertThat(second.executionAdjustedPnl()).isEqualTo(first.executionAdjustedPnl());
    }

    @Test
    void integratesBothCutoffsThroughTheRealHistoricalExecutionService() {
        HistoricalOrderBookPort port = mock(HistoricalOrderBookPort.class);
        when(port.replay(eq(MARKET_ID), eq(25), eq(ENTRY_CUTOFF))).thenReturn(replay(ENTRY_CUTOFF,
                "entry-book", "100", "100"));
        when(port.replay(eq(MARKET_ID), eq(25), eq(EXIT_CUTOFF))).thenReturn(replay(EXIT_CUTOFF,
                "exit-book", "110", "110"));

        var executionService = new HistoricalMarketExecutionService(port,
                new VisibleDepthMarketExecutionCalculator());
        var result = new HistoricalEconomicEvaluationService(executionService)
                .evaluate(request(StrategyIntent.Side.BUY, "1", unavailableCosts()));

        assertThat(result.executionAdjustedPnl()).isEqualByComparingTo("10");
        assertThat(result.entryMarketState().normalizationVersion()).isEqualTo("normalization-v1");
        assertThat(result.exitMarketState().providerSymbol()).isEqualTo("PF_XBTUSD");
        verify(port).replay(eq(MARKET_ID), eq(25), eq(ENTRY_CUTOFF));
        verify(port).replay(eq(MARKET_ID), eq(25), eq(EXIT_CUTOFF));
    }

    private HistoricalEconomicEvaluationService service(HistoricalMarketExecutionService executionService) {
        return new HistoricalEconomicEvaluationService(executionService, new EconomicLifecycleEvaluator());
    }

    private HistoricalEconomicEvaluationRequest request(StrategyIntent.Side side, String quantity,
                                                         TransactionCosts costs) {
        return request(intent(side, quantity), costs);
    }

    private HistoricalEconomicEvaluationRequest request(StrategyIntent intent, TransactionCosts costs) {
        return new HistoricalEconomicEvaluationRequest(MARKET_ID, intent, ENTRY_CUTOFF, EXIT_CUTOFF,
                EXIT_CUTOFF, costs, "visible-depth-market-execution-v1", "cost-v1");
    }

    private StrategyIntent intent(StrategyIntent.Side side, String quantity) {
        return new StrategyIntent("BTC/USD", side, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), ENTRY_CUTOFF);
    }

    private StrategyIntent exitIntent(StrategyIntent.Side side, String quantity) {
        return new StrategyIntent("BTC/USD", side, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), EXIT_CUTOFF);
    }

    private ReferenceMarketState state(EconomicProductType product, Instant cutoff, String reference) {
        BigDecimal value = new BigDecimal(reference);
        return new ReferenceMarketState(MARKET_ID, "BTC/USD", cutoff, value.subtract(BigDecimal.ONE),
                value.add(BigDecimal.ONE), value, product, cutoff.minusSeconds(1), product.name() + "-" + cutoff);
    }

    private SimulatedExecution filled(StrategyIntent.Side side, String quantity, String price, Instant at) {
        BigDecimal value = new BigDecimal(quantity);
        return new SimulatedExecution(side, value, value, new BigDecimal(price), at,
                SimulatedExecutionStatus.FILLED, "visible-depth-market-execution-v1", side.name() + "-book");
    }

    private HistoricalOrderBookPort.Replay replay(Instant cutoff, String fingerprint,
                                                   String ask, String bid) {
        UUID sequenceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new HistoricalOrderBookPort.Replay(HistoricalOrderBookPort.Status.AVAILABLE, MARKET_ID, 25,
                sequenceId, cutoff, cutoff.minusSeconds(1), cutoff.minusSeconds(1), 0L, 1L,
                "KRAKEN", "PF_XBTUSD", "normalization-v1", fingerprint,
                List.of(new HistoricalOrderBookPort.Level(new BigDecimal(bid), BigDecimal.ONE)),
                List.of(new HistoricalOrderBookPort.Level(new BigDecimal(ask), BigDecimal.ONE)), null);
    }

    private TransactionCosts unavailableCosts() {
        return new TransactionCosts(BigDecimal.ZERO, ExplicitCost.unavailable(), ExplicitCost.unavailable(),
                ExplicitCost.notApplicable(), null, "cost-v1");
    }

    private TransactionCosts fullyCostedSpotCosts() {
        return new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(BigDecimal.ZERO, null, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), BigDecimal.ZERO, "cost-v1");
    }
}
