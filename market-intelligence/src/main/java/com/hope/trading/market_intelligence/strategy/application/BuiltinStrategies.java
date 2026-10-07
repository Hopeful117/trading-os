package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.SemanticInputType;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyOperationalStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameter;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameters;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Built-in code-defined strategy definitions. Deterministic identity, no
 * hidden mutable configuration; the bootstrap legacy strategy exists purely as
 * a behavior-preserving migration vehicle and is UNVALIDATED by construction.
 *
 * <p>Story 0014: carries two production strategy definitions: the bootstrap
 * legacy compatibility fixture and the OHLC range-expansion setup.</p>
 */
@Component
public final class BuiltinStrategies {
    private static final String CRYPTO_MARKET = "CRYPTO";
    static final String VALIDITY_DURATION = "validityDuration";
    static final String HORIZON = "horizon";

    public BuiltinStrategies() {
        // Spring instantiates this component; all strategy definitions are stateless.
    }

    /** Fixed logical identity of the bootstrap OHLC trend strategy. */
    public static final UUID LEGACY_OHLC_TREND_ID =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001");

    public static final String LEGACY_OHLC_TREND_TYPE = "LEGACY_OHLC_TREND_V1";
    public static final int LEGACY_OHLC_TREND_VERSION = 1;
    public static final String LEGACY_OHLC_TREND_SCENARIO = "OHLC_TREND";

    /**
     * Semantic input keys use the canonical UPPER_SNAKE_CASE form. Generic
     * resolution maps them mechanically to camelCase observation evidence
     * measurement keys (PRICE_CHANGE -> priceChange, ADR-035 I-3/I-16).
     * OBSERVED_AT is reserved for the evidence timestamp metadata.
     */
    public static final RequiredSemanticInput PRICE_CHANGE = new RequiredSemanticInput(
            SemanticInputType.OBSERVATION, "PRICE_CHANGE");
    /** Semantic input key carrying the observation timestamp. */
    public static final RequiredSemanticInput OBSERVED_AT =
            StrategyEvaluationContextFactory.EVIDENCE_TIME_KEY;
    public static final String CONDITION_DIRECTIONAL_CHANGE = "directional_price_change";

    // ---- Second production strategy: OHLC Range Expansion (Story 0014) ----

    public static final UUID OHLC_RANGE_EXPANSION_ID =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789002");
    public static final String OHLC_RANGE_EXPANSION_TYPE = "OHLC_RANGE_EXPANSION_V1";
    public static final int OHLC_RANGE_EXPANSION_VERSION = 1;
    public static final String OHLC_RANGE_EXPANSION_SCENARIO = "RANGE_EXPANSION";

    /** Semantic input carrying the high-to-low range as percentage of lowest price. */
    public static final RequiredSemanticInput RANGE_PERCENTAGE = new RequiredSemanticInput(
            SemanticInputType.OBSERVATION, "RANGE_PERCENTAGE");
    public static final String CONDITION_SIGNIFICANT_MOVE = "significant_directional_move";
    public static final String CONDITION_RANGE_EXPANSION = "range_expansion";

    public static final UUID CONSERVATIVE_TREND_FOLLOWING_ID =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789003");
    public static final String CONSERVATIVE_TREND_FOLLOWING_TYPE = "CONSERVATIVE_TREND_FOLLOWING_V1";
    public static final int CONSERVATIVE_TREND_FOLLOWING_VERSION = 1;
    public static final String CONSERVATIVE_TREND_FOLLOWING_SCENARIO = "TREND_CONTEXT";
    public static final RequiredSemanticInput TREND_ATTENTION = trendInput("ATTENTION");
    public static final RequiredSemanticInput TREND_DIRECTION = trendInput("DIRECTION");
    public static final RequiredSemanticInput TREND_REGIME = trendInput("REGIME");
    public static final RequiredSemanticInput TREND_PHASE = trendInput("PHASE");
    public static final RequiredSemanticInput TREND_ALIGNMENT = trendInput("ALIGNMENT");
    public static final RequiredSemanticInput TREND_HARD_EXCLUSION = trendInput("HARD_EXCLUSION");
    public static final RequiredSemanticInput TREND_CONTRADICTION = trendInput("MATERIAL_CONTRADICTION");
    public static final RequiredSemanticInput TREND_INVALIDATION = trendInput("INVALIDATION");
    public static final RequiredSemanticInput TREND_CUTOFF_AT = trendInput("CUTOFF_AT");
    public static final RequiredSemanticInput TREND_VALID = trendInput("VALID");

    private static RequiredSemanticInput trendInput(String key) {
        return new RequiredSemanticInput(SemanticInputType.OBSERVATION, "TREND_CONTEXT_" + key);
    }

    public StrategyDefinition ohlcRangeExpansion() {
        return StrategyDefinition.create(
                new StrategyId(OHLC_RANGE_EXPANSION_ID),
                OHLC_RANGE_EXPANSION_VERSION,
                "OHLC Range Expansion",
                "Volatility-expansion setup: a directional price change that is "
                        + "significant in absolute terms AND occurs within a "
                        + "substantial high-to-low range. Direction follows the sign "
                        + "of the price change. NOT quantitatively validated.",
                OHLC_RANGE_EXPANSION_SCENARIO,
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(
                         Set.of(CRYPTO_MARKET),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of("KRAKEN")),
                Set.of(PRICE_CHANGE, RANGE_PERCENTAGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                        new StrategyParameter("minimumAbsoluteChange",
                                StrategyParameter.ParameterType.DECIMAL, new BigDecimal("1")),
                        new StrategyParameter("minimumRangePercentage",
                                StrategyParameter.ParameterType.DECIMAL, new BigDecimal("1")),
                         new StrategyParameter(VALIDITY_DURATION,
                                StrategyParameter.ParameterType.DURATION, Duration.ofMinutes(30)),
                         new StrategyParameter(HORIZON,
                                StrategyParameter.ParameterType.STRING, "15m"))),
                null,
                Instant.EPOCH);
    }

    public StrategyDefinition legacyOhlcTrend() {
        // Governance (ADR-036): the bootstrap fixture is UNVALIDATED by truth
        // (ADR-034 forbids labeling it quantitatively validated) and runs under
        // the explicit temporary BOOTSTRAP_CONTROLLED_RUN operational state,
        // expressed purely as definition data — no orchestration exception.
        return StrategyDefinition.create(
                new StrategyId(LEGACY_OHLC_TREND_ID),
                LEGACY_OHLC_TREND_VERSION,
                "Legacy OHLC Trend",
                "Bootstrap migration vehicle porting the legacy OHLC trend "
                        + "observation rule. Condition is intentionally permissive "
                        + "(any nonzero price change). NOT quantitatively validated; "
                        + "runs under controlled bootstrap evaluation with shadow "
                        + "parity monitoring.",
                LEGACY_OHLC_TREND_SCENARIO,
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(
                         Set.of(CRYPTO_MARKET, "FOREX", "STOCK", "INDEX", "COMMODITY"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of()),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                         new StrategyParameter(VALIDITY_DURATION,
                                StrategyParameter.ParameterType.DURATION, Duration.ofMinutes(30)),
                         new StrategyParameter(HORIZON,
                                StrategyParameter.ParameterType.STRING, "15m"))),
                null,
                Instant.EPOCH)
                .transitionTo(StrategyOperationalStatus.BOOTSTRAP_CONTROLLED_RUN, Instant.EPOCH);
    }

    public StrategyDefinition conservativeTrendFollowing() {
        return StrategyDefinition.create(
                new StrategyId(CONSERVATIVE_TREND_FOLLOWING_ID),
                CONSERVATIVE_TREND_FOLLOWING_VERSION,
                "Conservative Trend Following V1",
                "Conservative setup criteria over persisted Trend Context evidence. Disabled and unvalidated.",
                CONSERVATIVE_TREND_FOLLOWING_SCENARIO,
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of(CRYPTO_MARKET),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of("KRAKEN")),
                Set.of(TREND_ATTENTION, TREND_DIRECTION, TREND_REGIME, TREND_PHASE,
                        TREND_ALIGNMENT, TREND_HARD_EXCLUSION, TREND_CONTRADICTION,
                        TREND_INVALIDATION, TREND_CUTOFF_AT, TREND_VALID),
                StrategyParameters.empty(), null, Instant.EPOCH);
    }

    public List<StrategyDefinition> all() {
        return List.of(legacyOhlcTrend(), ohlcRangeExpansion(), conservativeTrendFollowing());
    }
}

/**
 * Deterministic evaluator for the bootstrap legacy OHLC trend semantics:
 *
 * <ul>
 *   <li>priceChange &gt; 0 → MATCH LONG</li>
 *   <li>priceChange &lt; 0 → MATCH SHORT</li>
 *   <li>priceChange == 0 → NO_MATCH (normal outcome, not failure)</li>
 *   <li>missing required inputs → NOT_EVALUABLE</li>
 * </ul>
 */
@Component
class LegacyOhlcTrendEvaluator implements StrategyEvaluator {

    @Override
    public String strategyType() {
        return BuiltinStrategies.LEGACY_OHLC_TREND_TYPE;
    }

    @Override
    public boolean supports(StrategyDefinition definition) {
        return BuiltinStrategies.LEGACY_OHLC_TREND_ID.equals(definition.strategyId().value())
                && definition.version() == BuiltinStrategies.LEGACY_OHLC_TREND_VERSION;
    }

    @Override
    public StrategyEvaluation evaluate(
            StrategyDefinition definition, StrategyEvaluationContext context) {
        for (RequiredSemanticInput required : definition.requiredInputs()) {
            if (!context.has(required)) {
                return StrategyEvaluation.notEvaluable(
                        definition, context, "Required semantic input missing: " + required);
            }
        }
        BigDecimal priceChange = context.get(BuiltinStrategies.PRICE_CHANGE).decimalValue();
        int signum = priceChange.signum();

        if (signum == 0) {
            return StrategyEvaluation.noMatch(
                    definition,
                    context,
                    List.of(ConditionResult.of(
                            BuiltinStrategies.CONDITION_DIRECTIONAL_CHANGE, false, priceChange)),
                    "No directional signal: price change is zero",
                    Set.of(BuiltinStrategies.PRICE_CHANGE));
        }

        MatchedDirection direction = signum > 0 ? MatchedDirection.LONG : MatchedDirection.SHORT;
        Duration validity = validityDuration(definition);
        Instant observedAt = observedAt(context);
        Instant validUntil = observedAt.plus(validity);

        return StrategyEvaluation.match(
                definition,
                context,
                direction,
                List.of(ConditionResult.of(
                        BuiltinStrategies.CONDITION_DIRECTIONAL_CHANGE, true, priceChange)),
                BigDecimal.ONE,
                "Directional OHLC trend: " + direction.name().toLowerCase()
                        + " with validity until " + validUntil
                        + " (" + horizon(definition) + ")",
                Set.of(BuiltinStrategies.PRICE_CHANGE));
    }

    private Duration validityDuration(StrategyDefinition definition) {
        return definition.parameters().find(BuiltinStrategies.VALIDITY_DURATION)
                .map(StrategyParameter::durationValue)
                .orElse(Duration.ofMinutes(30));
    }

    private String horizon(StrategyDefinition definition) {
        return definition.parameters().find(BuiltinStrategies.HORIZON)
                .map(StrategyParameter::stringValue)
                .orElse("15m");
    }

    private static Instant observedAt(StrategyEvaluationContext context) {
        return context.get(BuiltinStrategies.OBSERVED_AT).instantValue();
    }

    private static final RequiredSemanticInput PRICE_CHANGE = BuiltinStrategies.PRICE_CHANGE;
}

@Component
class ConservativeTrendFollowingEvaluator implements StrategyEvaluator {
    @Override
    public String strategyType() { return BuiltinStrategies.CONSERVATIVE_TREND_FOLLOWING_TYPE; }

    @Override
    public boolean supports(StrategyDefinition definition) {
        return BuiltinStrategies.CONSERVATIVE_TREND_FOLLOWING_ID.equals(definition.strategyId().value())
                && definition.version() == BuiltinStrategies.CONSERVATIVE_TREND_FOLLOWING_VERSION;
    }

    @Override
    public StrategyEvaluation evaluate(StrategyDefinition definition, StrategyEvaluationContext context) {
        if (context.provenance() == null || !hasAll(context, definition.requiredInputs())) {
            return StrategyEvaluation.notEvaluable(definition, context,
                    "Current Trend Context evidence is unavailable or incomplete");
        }
        var provenance = context.provenance();
        boolean valid = Boolean.parseBoolean(context.get(BuiltinStrategies.TREND_VALID).stringValue())
                && !provenance.cutOffAt().isAfter(context.evaluatedAt());
        boolean attractive = value(context, BuiltinStrategies.TREND_ATTENTION, "CONTEXTUALLY_ATTRACTIVE");
        String direction = context.get(BuiltinStrategies.TREND_DIRECTION).stringValue();
        boolean directional = direction.equals("UP") || direction.equals("DOWN");
        boolean regime = value(context, BuiltinStrategies.TREND_REGIME, "TRENDING");
        String phase = context.get(BuiltinStrategies.TREND_PHASE).stringValue();
        boolean acceptedPhase = phase.equals("DIRECTIONAL") || phase.equals("PULLBACK");
        String alignment = context.get(BuiltinStrategies.TREND_ALIGNMENT).stringValue();
        boolean compatible = direction.equals("UP")
                ? alignment.equals("ALIGNED_UP") || alignment.equals("PULLBACK_WITHIN_UP_BIAS")
                : alignment.equals("ALIGNED_DOWN") || alignment.equals("PULLBACK_WITHIN_DOWN_BIAS");
        boolean excluded = Boolean.parseBoolean(context.get(BuiltinStrategies.TREND_HARD_EXCLUSION).stringValue());
        boolean contradicted = Boolean.parseBoolean(context.get(BuiltinStrategies.TREND_CONTRADICTION).stringValue());
        boolean invalidated = Boolean.parseBoolean(context.get(BuiltinStrategies.TREND_INVALIDATION).stringValue());
        List<ConditionResult> conditions = List.of(
                new ConditionResult("attention_contextually_attractive", attractive, context.get(BuiltinStrategies.TREND_ATTENTION).stringValue()),
                new ConditionResult("directional_bias", directional, direction),
                new ConditionResult("trending_regime", regime, context.get(BuiltinStrategies.TREND_REGIME).stringValue()),
                new ConditionResult("accepted_phase", acceptedPhase, phase),
                new ConditionResult("compatible_alignment", compatible, alignment),
                new ConditionResult("no_hard_exclusion", !excluded, Boolean.toString(excluded)),
                new ConditionResult("no_material_contradiction", !contradicted, Boolean.toString(contradicted)),
                new ConditionResult("no_analytical_invalidation", !invalidated, Boolean.toString(invalidated)),
                new ConditionResult("current_valid_evidence", valid, provenance.cutOffAt().toString()));
        if (!(attractive && directional && regime && acceptedPhase && compatible
                && !excluded && !contradicted && !invalidated && valid)) {
            return StrategyEvaluation.noMatch(definition, context, conditions,
                    "Conservative Trend Following criteria were not all satisfied",
                    definition.requiredInputs());
        }
        MatchedDirection matched = direction.equals("UP") ? MatchedDirection.LONG : MatchedDirection.SHORT;
        return StrategyEvaluation.match(definition, context, matched, conditions, BigDecimal.ONE,
                "Conservative Trend Context criteria satisfied", definition.requiredInputs());
    }

    private static boolean hasAll(StrategyEvaluationContext context, Set<RequiredSemanticInput> inputs) {
        return inputs.stream().allMatch(context::has);
    }

    private static boolean value(StrategyEvaluationContext context, RequiredSemanticInput input, String expected) {
        return context.get(input).stringValue().equals(expected);
    }
}
