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

    public BuiltinStrategies() {
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
                        Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of("KRAKEN")),
                Set.of(PRICE_CHANGE, RANGE_PERCENTAGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                        new StrategyParameter("minimumAbsoluteChange",
                                StrategyParameter.ParameterType.DECIMAL, new BigDecimal("1")),
                        new StrategyParameter("minimumRangePercentage",
                                StrategyParameter.ParameterType.DECIMAL, new BigDecimal("1")),
                        new StrategyParameter("validityDuration",
                                StrategyParameter.ParameterType.DURATION, Duration.ofMinutes(30)),
                        new StrategyParameter("horizon",
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
                        Set.of("CRYPTO", "FOREX", "STOCK", "INDEX", "COMMODITY"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of()),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                        new StrategyParameter("validityDuration",
                                StrategyParameter.ParameterType.DURATION, Duration.ofMinutes(30)),
                        new StrategyParameter("horizon",
                                StrategyParameter.ParameterType.STRING, "15m"))),
                null,
                Instant.EPOCH)
                .transitionTo(StrategyOperationalStatus.BOOTSTRAP_CONTROLLED_RUN, Instant.EPOCH);
    }

    public List<StrategyDefinition> all() {
        return List.of(legacyOhlcTrend(), ohlcRangeExpansion());
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
        if (!context.has(BuiltinStrategies.PRICE_CHANGE)) {
            return StrategyEvaluation.notEvaluable(
                    definition, context, "Required semantic input missing: " + PRICE_CHANGE);
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
        return definition.parameters().find("validityDuration")
                .map(StrategyParameter::durationValue)
                .orElse(Duration.ofMinutes(30));
    }

    private String horizon(StrategyDefinition definition) {
        return definition.parameters().find("horizon")
                .map(StrategyParameter::stringValue)
                .orElse("15m");
    }

    private static Instant observedAt(StrategyEvaluationContext context) {
        // Validity derives from supplied timestamps only; absent observedAt
        // falls back to the context evaluation time, never wall-clock time.
        return context.has(BuiltinStrategies.OBSERVED_AT)
                ? context.get(BuiltinStrategies.OBSERVED_AT).instantValue()
                : context.evaluatedAt();
    }

    private static final RequiredSemanticInput PRICE_CHANGE = BuiltinStrategies.PRICE_CHANGE;
}
