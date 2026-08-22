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
 * <p>Story 0013: Production BuiltinStrategies remains limited to the existing
 * compatibility fixture. Additional strategies for architectural proof exist
 * only in test source.</p>
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

    /** Semantic input key carrying the OHLC first-to-last price change. */
    public static final RequiredSemanticInput PRICE_CHANGE = new RequiredSemanticInput(
            SemanticInputType.OBSERVATION, "OHLC_PRICE_CHANGE");
    /** Semantic input key carrying the observation timestamp. */
    public static final RequiredSemanticInput OBSERVED_AT = new RequiredSemanticInput(
            SemanticInputType.OBSERVATION, "OHLC_OBSERVED_AT");
    public static final String CONDITION_DIRECTIONAL_CHANGE = "directional_price_change";

    public StrategyDefinition legacyOhlcTrend() {
        return StrategyDefinition.create(
                new StrategyId(LEGACY_OHLC_TREND_ID),
                LEGACY_OHLC_TREND_VERSION,
                "Legacy OHLC Trend",
                "Bootstrap migration vehicle porting the legacy OHLC trend "
                        + "observation rule. Condition is intentionally permissive "
                        + "(any nonzero price change). NOT quantitatively validated.",
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
                Instant.EPOCH);
    }

    public List<StrategyDefinition> all() {
        return List.of(legacyOhlcTrend());
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
