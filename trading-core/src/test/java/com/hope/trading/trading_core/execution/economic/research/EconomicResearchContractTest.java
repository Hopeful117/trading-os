package com.hope.trading.trading_core.execution.economic.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.CostEvidence;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.EconomicLifecycleEvaluator;
import com.hope.trading.trading_core.execution.domain.economic.EconomicProductType;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.ExplicitCost;
import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecution;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;
import com.hope.trading.trading_core.execution.domain.economic.research.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EconomicResearchContractTest {
    private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CUTOFF = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    void rejectsInvalidPoliciesAndIdentities() {
        assertThatThrownBy(() -> new FixedBaseQuantityPolicy("v1", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeHorizonExitPolicy("v1", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StrategyHypothesisIdentity(" ", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionIdentityIsIndependentOfCollectionInsertionOrder() {
        var first = definition(List.of(MARKET_ID, UUID.fromString("00000000-0000-0000-0000-000000000002")),
                Set.of(SimulatedExecutionStatus.FILLED, SimulatedExecutionStatus.PARTIALLY_FILLED));
        var second = definition(List.of(UUID.fromString("00000000-0000-0000-0000-000000000002"), MARKET_ID),
                Set.of(SimulatedExecutionStatus.PARTIALLY_FILLED, SimulatedExecutionStatus.FILLED));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    void candidateIdentityChangesWithTradingSemantics() {
        var first = candidate("1", StrategyIntent.Side.BUY, "hypothesis-v1", "execution-v1", "cost-v1");
        var quantityChanged = candidate("2", StrategyIntent.Side.BUY, "hypothesis-v1", "execution-v1", "cost-v1");
        var sideChanged = candidate("1", StrategyIntent.Side.SELL, "hypothesis-v1", "execution-v1", "cost-v1");
        var hypothesisChanged = candidate("1", StrategyIntent.Side.BUY, "hypothesis-v2", "execution-v1", "cost-v1");
        var executionChanged = candidate("1", StrategyIntent.Side.BUY, "hypothesis-v1", "execution-v2", "cost-v1");

        assertThat(first.fingerprint()).isNotEqualTo(quantityChanged.fingerprint());
        assertThat(first.fingerprint()).isNotEqualTo(sideChanged.fingerprint());
        assertThat(first.fingerprint()).isNotEqualTo(hypothesisChanged.fingerprint());
        assertThat(first.fingerprint()).isNotEqualTo(executionChanged.fingerprint());
    }

    @Test
    void candidateUsesSemanticFeatureReferenceAndCanonicalSerialization() {
        var candidate = candidate("1", StrategyIntent.Side.BUY, "hypothesis-v1", "execution-v1", "cost-v1");
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var timestampSerializer = new EconomicResearchCanonicalSerializer(new ObjectMapper()
                .findAndRegisterModules().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

        String first = serializer.candidate(candidate);
        String second = serializer.candidate(candidate);

        assertThat(first).isEqualTo(second)
                .isEqualTo(timestampSerializer.candidate(candidate))
                .contains("featureSnapshotFingerprint")
                .contains("canonical-feature-identity")
                .doesNotContain("values")
                .doesNotContain("analysisExecutionId");
    }

    @Test
    void resultPreservesUnavailableEconomicStateWithoutInventingNetValues() {
        EconomicResult economicResult = EconomicResult.unavailable(
                EconomicResultStatus.EXECUTION_UNAVAILABLE,
                CostCompleteness.INSUFFICIENT_EVIDENCE,
                "entry execution unavailable");
        var result = new EconomicResearchResult("candidate-fingerprint", null, economicResult,
                EconomicResearchResultDisposition.EXECUTION_UNAVAILABLE, "entry execution unavailable", null);
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());

        assertThat(result.resultFingerprint()).hasSize(64);
        assertThat(economicResult.netPnl()).isNull();
        assertThat(serializer.result(result)).contains("INSUFFICIENT_EVIDENCE");
        assertThat(serializer.result(result)).contains("EXECUTION_UNAVAILABLE");
    }

    @Test
    void serializerPreservesFullyCostedExecutionEconomics() {
        StrategyIntent intent = new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY,
                StrategyIntent.OrderType.MARKET, BigDecimal.ONE, CUTOFF.minusSeconds(1));
        Instant exitAt = CUTOFF.plus(Duration.ofHours(1));
        SimulatedExecution entry = filled(StrategyIntent.Side.BUY, "1", "101", CUTOFF);
        SimulatedExecution exit = filled(StrategyIntent.Side.SELL, "1", "109", exitAt);
        CostEvidence evidence = new CostEvidence("fee", "fixture", CUTOFF.minusSeconds(60),
                exitAt.plusSeconds(60), CUTOFF.minusSeconds(30));
        TransactionCosts costs = new TransactionCosts(BigDecimal.ZERO,
                ExplicitCost.available(new BigDecimal("0.2"), evidence, "cost-v1"),
                ExplicitCost.notApplicable(), ExplicitCost.notApplicable(), new BigDecimal("0.2"), "cost-v1");
        EconomicResult economicResult = new EconomicLifecycleEvaluator().evaluate(intent,
                state("100", CUTOFF), state("110", exitAt), entry, exit, costs,
                "execution-v1", "cost-v1");
        var result = new EconomicResearchResult("candidate-fingerprint", "outcome-fingerprint", economicResult,
                EconomicResearchResultDisposition.EVALUATED, null, null);
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());

        assertThat(economicResult.status()).isEqualTo(EconomicResultStatus.AVAILABLE);
         assertThat(serializer.result(result)).contains("entryExecution", "exitExecution", "entryMarketState",
                 "exitMarketState", "commission", "sourceId");
    }

    @Test
    void enforcesFixedQuantityAndComputesExitTime() {
        var exit = new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1));
        assertThat(exit.exitAt(CUTOFF)).isEqualTo(CUTOFF.plus(Duration.ofHours(1)));
        assertThatThrownBy(this::invalidCandidate)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manifestRejectsInconsistentFingerprintCountsAndHasStableIdentity() {
        assertThatThrownBy(() -> new EconomicResearchDatasetManifest("economic-v1", "definition", 1, 0,
                Map.of(), Map.of(), Map.of(), BigDecimal.ZERO, 0, List.of(), List.of(),
                "candidates", "results", "bundle"))
                .isInstanceOf(IllegalArgumentException.class);

        var manifest = new EconomicResearchDatasetManifest("economic-v1", "definition", 1, 1,
                Map.of("FILLED", 1), Map.of("FULLY_COSTED", 1),
                Map.of(EconomicResearchResultDisposition.EVALUATED, 1), BigDecimal.ZERO, 0,
                List.of("candidate"), List.of("result"), "candidates", "results", "bundle");
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());

        assertThat(manifest.fingerprint()).hasSize(64);
        assertThat(serializer.manifest(manifest)).contains("manifestFingerprint");
    }

    @Test
    void rejectsInvalidDefinitionAndResultCombinations() {
        List<UUID> noMarkets = List.of();
        Set<SimulatedExecutionStatus> filledStatus = Set.of(SimulatedExecutionStatus.FILLED);
        List<UUID> duplicateMarkets = List.of(MARKET_ID, MARKET_ID);
        List<UUID> oneMarket = List.of(MARKET_ID);
        Set<SimulatedExecutionStatus> noStatuses = Set.of();
        assertThatThrownBy(() -> definition(noMarkets, filledStatus))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definition(duplicateMarkets, filledStatus))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definition(oneMarket, noStatuses))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::invalidManifest)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::invalidEvaluatedResult)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::invalidCandidateDefinition)
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(this::invalidResultFingerprint)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new EconomicResearchResult("candidate", null, null,
                EconomicResearchResultDisposition.EXCLUDED, "excluded", null).resultFingerprint())
                .hasSize(64);

        assertThatThrownBy(this::invalidLimitCandidate)
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(this::invalidExecutionCountsManifest)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::duplicateFingerprintManifest)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::invalidResidualManifest)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::outOfPopulationCandidate)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::outOfBoundsCandidate)
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EconomicResearchDatasetManifest invalidManifest() {
        return new EconomicResearchDatasetManifest("economic-v1", "definition", -1, 0,
                Map.of(), Map.of(), Map.of(), BigDecimal.ZERO, 0, List.of(), List.of(),
                "candidates", "results", "bundle");
    }

    private EconomicResearchResult invalidEvaluatedResult() {
        return new EconomicResearchResult("candidate", null, null,
                EconomicResearchResultDisposition.EVALUATED, null, null);
    }

    private EconomicResearchResult invalidResultFingerprint() {
        return new EconomicResearchResult("candidate", null, null,
                EconomicResearchResultDisposition.EXCLUDED, "excluded", "explicit-result");
    }

    private EconomicResearchCandidate invalidCandidateDefinition() {
        return new EconomicResearchCandidate(
                candidateDefinition("1", "hypothesis-v1", "execution-v1", "cost-v1"), featureReference(),
                new StrategyHypothesisIdentity("hypothesis", "hypothesis-v2"),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF), new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE),
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), "execution-v1", "cost-v1");
    }

    private EconomicResearchCandidate invalidLimitCandidate() {
        return new EconomicResearchCandidate(candidateDefinition("1", "v1", "execution-v1", "cost-v1"),
                featureReference(), new StrategyHypothesisIdentity("hypothesis", "v1"),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.LIMIT,
                        BigDecimal.ONE, CUTOFF), new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE),
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), "execution-v1", "cost-v1");
    }

    private EconomicResearchDatasetManifest invalidExecutionCountsManifest() {
        return new EconomicResearchDatasetManifest("economic-v1", "definition", 1, 1,
                Map.of("FILLED", 2), Map.of(), Map.of(EconomicResearchResultDisposition.EXCLUDED, 1),
                BigDecimal.ZERO, 0, List.of("candidate"), List.of("result"), "candidates", "results", "bundle");
    }

    private EconomicResearchDatasetManifest duplicateFingerprintManifest() {
        return new EconomicResearchDatasetManifest("economic-v1", "definition", 2, 1,
                Map.of(), Map.of(), Map.of(EconomicResearchResultDisposition.EXCLUDED, 1), BigDecimal.ZERO, 0,
                List.of("candidate", "candidate"), List.of("result"), "candidates", "results", "bundle");
    }

    private EconomicResearchDatasetManifest invalidResidualManifest() {
        return new EconomicResearchDatasetManifest("economic-v1", "definition", 1, 1,
                Map.of(), Map.of(), Map.of(EconomicResearchResultDisposition.EXCLUDED, 1), BigDecimal.ONE, 0,
                List.of("candidate"), List.of("result"), "candidates", "results", "bundle");
    }

    private EconomicResearchCandidate outOfPopulationCandidate() {
        return new EconomicResearchCandidate(candidateDefinition("1", "v1", "execution-v1", "cost-v1"),
                featureReference(UUID.fromString("00000000-0000-0000-0000-000000000002"), CUTOFF),
                new StrategyHypothesisIdentity("hypothesis", "v1"),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF), new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE),
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), "execution-v1", "cost-v1");
    }

    private EconomicResearchCandidate outOfBoundsCandidate() {
        return new EconomicResearchCandidate(candidateDefinition("1", "v1", "execution-v1", "cost-v1"),
                featureReference(MARKET_ID, CUTOFF.plus(Duration.ofHours(2))),
                new StrategyHypothesisIdentity("hypothesis", "v1"),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF), new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE),
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), "execution-v1", "cost-v1");
    }

    private EconomicResearchCandidate invalidCandidate() {
        var sizing = new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE);
        var exit = new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1));
        StrategyIntent invalidIntent = new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY,
                StrategyIntent.OrderType.MARKET, new BigDecimal("2"), CUTOFF);
        return new EconomicResearchCandidate(candidateDefinition("1", "hypothesis-v1", "execution-v1", "cost-v1"), featureReference(),
                new StrategyHypothesisIdentity("hypothesis", "v1"), invalidIntent, sizing, exit,
                "execution-v1", "cost-v1");
    }

    private EconomicResearchCandidate candidate(String quantity, StrategyIntent.Side side,
                                                String hypothesisVersion, String executionVersion,
                                                String costVersion) {
        StrategyIntent intent = new StrategyIntent("BTC/USD", side, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), CUTOFF);
        return new EconomicResearchCandidate(candidateDefinition(quantity, hypothesisVersion,
                executionVersion, costVersion),
                featureReference(),
                new StrategyHypothesisIdentity("hypothesis", hypothesisVersion), intent,
                new FixedBaseQuantityPolicy("size-v1", new BigDecimal(quantity)),
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), executionVersion, costVersion);
    }

    private FeatureSnapshotReference featureReference() {
        return featureReference(MARKET_ID, CUTOFF);
    }

    private FeatureSnapshotReference featureReference(UUID marketId, Instant cutoff) {
        return new FeatureSnapshotReference(marketId, cutoff, "feature-v1", "context", "feature",
                "canonical-feature-identity");
    }

    private SimulatedExecution filled(StrategyIntent.Side side, String quantity, String price, Instant executedAt) {
        BigDecimal value = new BigDecimal(quantity);
        return new SimulatedExecution(side, value, value, new BigDecimal(price), executedAt,
                SimulatedExecutionStatus.FILLED, "execution-v1", side.name().toLowerCase() + "-source");
    }

    private ReferenceMarketState state(String referencePrice, Instant cutoff) {
        BigDecimal reference = new BigDecimal(referencePrice);
        return new ReferenceMarketState(MARKET_ID, "BTC/USD", cutoff, reference.subtract(BigDecimal.ONE),
                reference.add(BigDecimal.ONE), reference, EconomicProductType.SPOT,
                cutoff.minusSeconds(1), "market-state");
    }

    private EconomicResearchDatasetDefinition definition(List<UUID> markets,
                                                          Set<SimulatedExecutionStatus> statuses) {
        return new EconomicResearchDatasetDefinition("economic-v1", "population-v1", markets,
                CUTOFF.minus(Duration.ofHours(1)), CUTOFF.plus(Duration.ofHours(1)), "feature-v1", "evidence-v1",
                new StrategyHypothesisIdentity("hypothesis", "v1"),
                new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE), "entry-v1",
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), "execution-v1", "cost-v1",
                "cutoff-v1", CostCompleteness.FULLY_COSTED, statuses, null);
    }

    private EconomicResearchDatasetDefinition candidateDefinition(String quantity, String hypothesisVersion,
                                                                   String executionVersion, String costVersion) {
        return new EconomicResearchDatasetDefinition("economic-v1", "population-v1", List.of(MARKET_ID),
                CUTOFF.minus(Duration.ofHours(1)), CUTOFF.plus(Duration.ofHours(1)), "feature-v1", "evidence-v1",
                new StrategyHypothesisIdentity("hypothesis", hypothesisVersion),
                new FixedBaseQuantityPolicy("size-v1", new BigDecimal(quantity)), "entry-v1",
                new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1)), executionVersion, costVersion,
                "cutoff-v1", CostCompleteness.FULLY_COSTED, Set.of(SimulatedExecutionStatus.FILLED), null);
    }
}
