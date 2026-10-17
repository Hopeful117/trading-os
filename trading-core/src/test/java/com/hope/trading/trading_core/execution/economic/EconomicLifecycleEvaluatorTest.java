package com.hope.trading.trading_core.execution.economic;

import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.CostComponentStatus;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EconomicLifecycleEvaluatorTest {
    private static final Instant ENTRY_TIME = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant EXIT_TIME = Instant.parse("2026-10-17T11:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final EconomicLifecycleEvaluator evaluator = new EconomicLifecycleEvaluator();

    @Test
    void calculatesFullyCostedSpotLifecycleWithoutDoubleCountingSpread() {
        StrategyIntent intent = intent("2");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "2", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "2", "109", EXIT_TIME);
        TransactionCosts costs = costs("cost-v1", ExplicitCost.notApplicable());

        var result = evaluator.evaluate(intent, state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit, costs,
                "market-execution-v1", "cost-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.AVAILABLE);
        assertThat(result.completeness()).isEqualTo(CostCompleteness.FULLY_COSTED);
        assertThat(result.executionStatus()).isEqualTo(SimulatedExecutionStatus.FILLED);
        assertThat(result.referenceMarketReturn()).isEqualByComparingTo("0.1");
        assertThat(result.grossStrategyPnl()).isEqualByComparingTo("20");
        assertThat(result.executionAdjustedPnl()).isEqualByComparingTo("16");
        assertThat(result.netPnl()).isEqualByComparingTo("15.8");
        assertThat(result.netReturn()).isNull();
        assertThat(result.closedQuantity()).isEqualByComparingTo("2");
        assertThat(result.residualExposure()).isZero();
        assertThat(result.calculationFingerprint()).hasSize(64);
    }

    @Test
    void preservesPartialPerpetualExecutionAndUnavailableFunding() {
        StrategyIntent intent = intent("2");
        SimulatedExecution entry = new SimulatedExecution(StrategyIntent.Side.BUY,
                new BigDecimal("2"), new BigDecimal("1.7"), new BigDecimal("101"), ENTRY_TIME,
                SimulatedExecutionStatus.PARTIALLY_FILLED, "market-execution-v1", "entry-book");
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1.7", "109", EXIT_TIME);
        TransactionCosts costs = costs("cost-v1", ExplicitCost.unavailable());

        var result = evaluator.evaluate(intent, state(EconomicProductType.LINEAR_PERPETUAL, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.LINEAR_PERPETUAL, "110", "109", "111", EXIT_TIME), entry, exit, costs,
                "market-execution-v1", "cost-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.DEGRADED);
        assertThat(result.completeness()).isEqualTo(CostCompleteness.PARTIALLY_COSTED);
        assertThat(result.executionStatus()).isEqualTo(SimulatedExecutionStatus.PARTIALLY_FILLED);
        assertThat(entry.requestedQuantity()).isEqualByComparingTo("2");
        assertThat(entry.quantity()).isEqualByComparingTo("2");
        assertThat(entry.filledQuantity()).isEqualByComparingTo("1.7");
        assertThat(entry.unfilledQuantity()).isEqualByComparingTo("0.3");
        assertThat(result.netPnl()).isNull();
        assertThat(result.costs().fundingStatus()).isEqualTo(com.hope.trading.trading_core.execution.domain.economic.CostComponentStatus.UNAVAILABLE);
        assertThat(result.closedQuantity()).isEqualByComparingTo("1.7");
        assertThat(result.residualExposure()).isZero();
    }

    @Test
    void distinguishesAvailableZeroCommissionFromUnavailableCommission() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", EXIT_TIME);
        var entryState = state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME);
        var exitState = state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME);

        TransactionCosts availableZero = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(BigDecimal.ZERO, null, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), BigDecimal.ZERO, "cost-v1");
        TransactionCosts unavailable = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.unavailable(), ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), null, "cost-v1");

        var available = evaluator.evaluate(intent, entryState, exitState, entry, exit, availableZero,
                "market-execution-v1", "cost-v1");
        var missing = evaluator.evaluate(intent, entryState, exitState, entry, exit, unavailable,
                "market-execution-v1", "cost-v1");

        assertThat(available.completeness()).isEqualTo(CostCompleteness.FULLY_COSTED);
        assertThat(available.netPnl()).isEqualByComparingTo("8");
        assertThat(missing.completeness()).isEqualTo(CostCompleteness.PARTIALLY_COSTED);
        assertThat(missing.netPnl()).isNull();
        assertThat(available.calculationFingerprint()).isNotEqualTo(missing.calculationFingerprint());
    }

    @Test
    void distinguishesNotApplicableAndUnavailableFundingInResultIdentity() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", EXIT_TIME);

        var notApplicable = evaluator.evaluate(intent, state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit,
                costs("cost-v1", ExplicitCost.notApplicable()), "market-execution-v1", "cost-v1");
        var unavailable = evaluator.evaluate(intent, state(EconomicProductType.LINEAR_PERPETUAL, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.LINEAR_PERPETUAL, "110", "109", "111", EXIT_TIME), entry, exit,
                costs("cost-v1", ExplicitCost.unavailable()), "market-execution-v1", "cost-v1");

        assertThat(notApplicable.completeness()).isEqualTo(CostCompleteness.FULLY_COSTED);
        assertThat(unavailable.completeness()).isEqualTo(CostCompleteness.PARTIALLY_COSTED);
        assertThat(notApplicable.calculationFingerprint()).isNotEqualTo(unavailable.calculationFingerprint());
    }

    @Test
    void changesFingerprintWhenTransactionCostPolicyChanges() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", EXIT_TIME);
        var state = state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME);
        var exitState = state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME);

        var first = evaluator.evaluate(intent, state, exitState, entry, exit,
                costs("cost-v1", ExplicitCost.notApplicable()), "market-execution-v1", "cost-v1");
        var second = evaluator.evaluate(intent, state, exitState, entry, exit,
                costs("cost-v2", ExplicitCost.notApplicable()), "market-execution-v1", "cost-v2");

        assertThat(first.calculationFingerprint()).isNotEqualTo(second.calculationFingerprint());
    }

    @Test
    void rejectsCostEvidenceKnownAfterExecutionCutoff() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", EXIT_TIME);
        CostEvidence futureEvidence = new CostEvidence("future-fee", "fixture",
                ENTRY_TIME.minusSeconds(60), EXIT_TIME.plusSeconds(60), EXIT_TIME.plusSeconds(1));
        TransactionCosts costs = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(new BigDecimal("0.2"), futureEvidence, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), new BigDecimal("0.2"), "cost-v1");

        var result = evaluator.evaluate(intent, state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit, costs,
                "market-execution-v1", "cost-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.STALE);
        assertThat(result.netPnl()).isNull();

        CostEvidence differentFutureEvidence = new CostEvidence("future-fee-2", "fixture",
                ENTRY_TIME.minusSeconds(60), EXIT_TIME.plusSeconds(60), EXIT_TIME.plusSeconds(1));
        TransactionCosts differentCosts = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(new BigDecimal("0.2"), differentFutureEvidence, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), new BigDecimal("0.2"), "cost-v1");
        var different = evaluator.evaluate(intent,
                state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit,
                differentCosts, "market-execution-v1", "cost-v1");
        assertThat(different.calculationFingerprint()).isNotEqualTo(result.calculationFingerprint());
    }

    @Test
    void rejectsEntryEvidenceKnownAfterDecisionTime() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", ENTRY_TIME);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", EXIT_TIME);
        ReferenceMarketState entryState = stateWithKnownAt(EconomicProductType.SPOT, "100", "99", "101",
                ENTRY_TIME, ENTRY_TIME);

        var result = evaluator.evaluate(intent, entryState,
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit,
                costs("cost-v1", ExplicitCost.notApplicable()), "market-execution-v1", "cost-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.STALE);
    }

    @Test
    void classifiesZeroFillAsExecutionUnavailable() {
        StrategyIntent intent = intent("1");
        SimulatedExecution entry = new SimulatedExecution(StrategyIntent.Side.BUY,
                BigDecimal.ONE, BigDecimal.ZERO, null, ENTRY_TIME,
                SimulatedExecutionStatus.UNFILLED, "market-execution-v1", "entry-book");
        SimulatedExecution exit = new SimulatedExecution(StrategyIntent.Side.SELL,
                BigDecimal.ONE, BigDecimal.ZERO, null, EXIT_TIME,
                SimulatedExecutionStatus.UNFILLED, "market-execution-v1", "exit-book");

        var result = evaluator.evaluate(intent, state(EconomicProductType.SPOT, "100", "99", "101", ENTRY_TIME),
                state(EconomicProductType.SPOT, "110", "109", "111", EXIT_TIME), entry, exit,
                costs("cost-v1", ExplicitCost.notApplicable()), "market-execution-v1", "cost-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.EXECUTION_UNAVAILABLE);
        assertThat(result.grossStrategyPnl()).isNull();
    }

    @Test
    void classifiesAllUnavailableApplicableCostsAsInsufficientEvidence() {
        TransactionCosts costs = new TransactionCosts(BigDecimal.ZERO, ExplicitCost.unavailable(),
                ExplicitCost.unavailable(), ExplicitCost.unavailable(), null, "cost-v1");

        assertThat(costs.completeness()).isEqualTo(CostCompleteness.INSUFFICIENT_EVIDENCE);
        assertThat(costs.fundingComponent().status()).isEqualTo(CostComponentStatus.UNAVAILABLE);
    }

    @Test
    void rejectsAvailableEconomicResultWithoutRequiredInputs() {
        assertThatThrownBy(() -> new com.hope.trading.trading_core.execution.domain.economic.EconomicResult(
                EconomicResultStatus.AVAILABLE, CostCompleteness.FULLY_COSTED, BigDecimal.ONE, BigDecimal.ONE,
                null, null, null, null, null, null, null, SimulatedExecutionStatus.FILLED,
                null, null, null, null, null, "execution-v1", "cost-v1", "fingerprint", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeCommissionCost() {
        assertThatThrownBy(() -> new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(new BigDecimal("-0.1"), null, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), null, "cost-v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAuthoritativeNetPnlOnIncompleteLifecycle() {
        assertThatThrownBy(() -> new com.hope.trading.trading_core.execution.domain.economic.EconomicResult(
                EconomicResultStatus.DEGRADED, CostCompleteness.PARTIALLY_COSTED, null, null, null, null,
                null, null, null, null, null, SimulatedExecutionStatus.PARTIALLY_FILLED,
                null, null, null, BigDecimal.ONE, null, "execution-v1", "cost-v1", "fingerprint", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StrategyIntent intent(String quantity) {
        return new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), ENTRY_TIME.minusSeconds(1));
    }

    private SimulatedExecution filled(StrategyIntent.Side side, String quantity, String price, Instant executedAt) {
        BigDecimal value = new BigDecimal(quantity);
        return new SimulatedExecution(side, value, value, new BigDecimal(price), executedAt,
                SimulatedExecutionStatus.FILLED, "market-execution-v1", side.name().toLowerCase() + "-book");
    }

    private ReferenceMarketState state(EconomicProductType product, String reference, String bid, String ask,
                                       Instant cutoff) {
        return stateWithKnownAt(product, reference, bid, ask, cutoff, cutoff.minusSeconds(1));
    }

    private ReferenceMarketState stateWithKnownAt(EconomicProductType product, String reference, String bid,
                                                  String ask, Instant cutoff, Instant knownAt) {
        return new ReferenceMarketState(MARKET_ID, "BTC/USD", cutoff, new BigDecimal(bid), new BigDecimal(ask),
                new BigDecimal(reference), product, knownAt, product.name() + "-book");
    }

    private TransactionCosts costs(String policyVersion, ExplicitCost funding) {
        CostEvidence feeEvidence = new CostEvidence("fee-" + policyVersion, "fixture",
                ENTRY_TIME.minusSeconds(60), EXIT_TIME.plusSeconds(60), ENTRY_TIME.minusSeconds(30));
        ExplicitCost commission = ExplicitCost.available(new BigDecimal("0.2"), feeEvidence, policyVersion);
        return new TransactionCosts(BigDecimal.ZERO, commission, funding, ExplicitCost.notApplicable(),
                funding.contributesAmount() ? new BigDecimal("0.2").add(funding.amount()) : null,
                policyVersion);
    }
}
