package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFreshness;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactProvenance;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactScope;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.ArtifactCardinality;
import com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement;
import com.hope.trading.market_intelligence.domain.capability.Capability;
import com.hope.trading.market_intelligence.domain.capability.CapabilityCategory;
import com.hope.trading.market_intelligence.domain.capability.CapabilityContext;
import com.hope.trading.market_intelligence.domain.capability.CapabilityId;
import com.hope.trading.market_intelligence.domain.capability.CapabilityMetadata;
import com.hope.trading.market_intelligence.domain.capability.CapabilityResult;
import com.hope.trading.market_intelligence.domain.capability.ExecutionPolicy;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.capability.ProducedContribution;
import com.hope.trading.market_intelligence.domain.capability.RetryPolicy;
import com.hope.trading.market_intelligence.domain.capability.VersionCompatibilityMode;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;
import com.hope.trading.market_intelligence.domain.orderbook.OrderBookLiquidityCalculator;
import com.hope.trading.market_intelligence.domain.orderbook.OrderBookLiquidityFeatureSet;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class OrderBookLiquidityCapability implements Capability {
    public static final String CAPABILITY_ID = "order-book-liquidity";
    public static final String CAPABILITY_VERSION = "1.0.0";
    private final OrderBookLiquidityCalculator calculator;

    @Autowired
    public OrderBookLiquidityCapability() {
        this(new OrderBookLiquidityCalculator());
    }

    OrderBookLiquidityCapability(OrderBookLiquidityCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new com.hope.trading.market_intelligence.domain.capability.CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND, RetryPolicy.disabled(),
                List.of(new ArtifactRequirement(ProductionArtifactTypes.ORDER_BOOK_REPLAY,
                        ProductionArtifactTypes.V1, VersionCompatibilityMode.EXACT,
                        true, ArtifactCardinality.ONE, true)),
                List.of(new ProducedContribution.ArtifactContribution(
                        ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY,
                        ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY_V1, Set.of()),
                        new ProducedContribution.MetricContribution("liquidity")),
                Duration.ofSeconds(5), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        var input = context.resolvedArtifacts().values().stream()
                .flatMap(Collection::stream)
                .filter(value -> value.content() instanceof OrderBookContext)
                .findFirst();
        if (input.isEmpty()) return degraded("Order Book replay is unavailable");

        OrderBookContext orderBook = (OrderBookContext) input.get().content();
        OrderBookLiquidityFeatureSet result = calculator.calculate(orderBook);
        StoredArtifact artifact = new StoredArtifact(
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY.value(),
                                CAPABILITY_ID, CAPABILITY_VERSION),
                        ArtifactScope.publicMarket(orderBook.marketId(), "ORDER_BOOK", AnalysisExecutionMode.ACTIVE),
                        new ArtifactFingerprint(result.parameterFingerprint()),
                        new ArtifactFingerprint(result.resultFingerprint())),
                result,
                ArtifactFreshness.validUntil(result.latestKnownAt(),
                        result.latestKnownAt().plus(Duration.ofMinutes(15)), result.resultFingerprint()),
                new ArtifactProvenance(CAPABILITY_ID, CAPABILITY_VERSION,
                        context.capabilityExecutionId(), result.latestKnownAt(),
                        Set.of(input.get().key().identity()), Set.of())
                        .withEvidenceFingerprint(result.sourceEvidenceFingerprint())
                        .withContextFingerprint(input.get().provenance().contextFingerprint()),
                result.groups().stream().allMatch(group -> group.status() == FeatureMissingness.PRESENT)
                        ? AnalysisResultQuality.COMPLETE : AnalysisResultQuality.DEGRADED,
                ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY_V1);
        Map<String, BigDecimal> metrics = result.values().stream()
                .filter(value -> value instanceof NumericFeatureValue numeric
                        && numeric.status() == FeatureMissingness.PRESENT)
                .map(NumericFeatureValue.class::cast)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        NumericFeatureValue::name, NumericFeatureValue::value));
        List<String> diagnostics = result.groups().stream()
                .flatMap(group -> group.diagnostics().stream()).toList();
        return new CapabilityResult(metadata().producedContributions(),
                List.of(new ProducedArtifact(ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY,
                        ProductionArtifactTypes.ORDER_BOOK_LIQUIDITY_V1, artifact)),
                metrics, diagnostics,
                metrics.size() == result.values().size()
                        ? com.hope.trading.market_intelligence.domain.capability.CapabilityCompleteness.COMPLETE
                        : com.hope.trading.market_intelligence.domain.capability.CapabilityCompleteness.DEGRADED);
    }

    private CapabilityResult degraded(String message) {
        return new CapabilityResult(List.of(), List.of(), Map.of(), List.of(message),
                com.hope.trading.market_intelligence.domain.capability.CapabilityCompleteness.DEGRADED);
    }
}
