package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvidenceProvenance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Story0065TrendContextStrategyTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final UUID MARKET = UUID.fromString("eeeeeeee-1111-2222-3333-444444444444");

    @Test
    void conservativeDefinitionIsVersionedDisabledAndUnvalidated() {
        StrategyDefinition definition = new BuiltinStrategies().conservativeTrendFollowing();

        assertThat(definition.version()).isEqualTo(1);
        assertThat(definition.operationalStatus().name()).isEqualTo("DISABLED");
        assertThat(definition.validationStatus().name()).isEqualTo("UNVALIDATED");
        assertThat(definition.requiredInputs())
                .contains(BuiltinStrategies.TREND_ATTENTION, BuiltinStrategies.TREND_DIRECTION,
                        BuiltinStrategies.TREND_ALIGNMENT, BuiltinStrategies.TREND_CUTOFF_AT);
    }

    @Test
    void allConservativeCriteriaProduceDirectionalMatch() {
        StrategyDefinition definition = new BuiltinStrategies().conservativeTrendFollowing();
        StrategyEvaluationContext context = completeContext("UP", "CONTEXTUALLY_ATTRACTIVE",
                "ALIGNED_UP");

        var evaluation = new ConservativeTrendFollowingEvaluator().evaluate(definition, context);

        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).hasValue(com.hope.trading.market_intelligence.strategy.domain.MatchedDirection.LONG);
        assertThat(evaluation.contextDigest()).isEqualTo(context.digest());
    }

    @Test
    void attractiveAttentionAloneDoesNotMatch() {
        StrategyDefinition definition = new BuiltinStrategies().conservativeTrendFollowing();
        StrategyEvaluationContext context = completeContext("UP", "CONTEXTUALLY_ATTRACTIVE",
                "CONFLICTING");

        var evaluation = new ConservativeTrendFollowingEvaluator().evaluate(definition, context);

        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
        assertThat(evaluation.direction()).isEmpty();
    }

    @Test
    void missingTypedEvidenceIsNotEvaluable() {
        StrategyDefinition definition = new BuiltinStrategies().conservativeTrendFollowing();
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("BTC/EUR")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW).build();

        assertThat(new ConservativeTrendFollowingEvaluator().evaluate(definition, context).status())
                .isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
    }

    private static StrategyEvaluationContext completeContext(
            String direction, String attention, String alignment) {
        StrategyEvaluationContext.Builder builder = StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("BTC/EUR")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW)
                .provenance(new StrategyEvidenceProvenance(
                        UUID.randomUUID(), UUID.randomUUID(), 1,
                        Instant.parse("2026-08-22T11:55:00Z"), "profile-v1", "rule-v1",
                        "input-fingerprint", "assessment-fingerprint"));
        builder.input(BuiltinStrategies.TREND_ATTENTION,
                StrategyEvaluationContext.SemanticValue.string(attention));
        builder.input(BuiltinStrategies.TREND_DIRECTION,
                StrategyEvaluationContext.SemanticValue.string(direction));
        builder.input(BuiltinStrategies.TREND_REGIME,
                StrategyEvaluationContext.SemanticValue.string("TRENDING"));
        builder.input(BuiltinStrategies.TREND_PHASE,
                StrategyEvaluationContext.SemanticValue.string("DIRECTIONAL"));
        builder.input(BuiltinStrategies.TREND_ALIGNMENT,
                StrategyEvaluationContext.SemanticValue.string(alignment));
        builder.input(BuiltinStrategies.TREND_HARD_EXCLUSION,
                StrategyEvaluationContext.SemanticValue.string("false"));
        builder.input(BuiltinStrategies.TREND_CONTRADICTION,
                StrategyEvaluationContext.SemanticValue.string("false"));
        builder.input(BuiltinStrategies.TREND_INVALIDATION,
                StrategyEvaluationContext.SemanticValue.string("false"));
        builder.input(BuiltinStrategies.TREND_CUTOFF_AT,
                StrategyEvaluationContext.SemanticValue.instant(
                        Instant.parse("2026-08-22T11:55:00Z")));
        builder.input(BuiltinStrategies.TREND_VALID,
                StrategyEvaluationContext.SemanticValue.string("true"));
        return builder.build();
    }
}
