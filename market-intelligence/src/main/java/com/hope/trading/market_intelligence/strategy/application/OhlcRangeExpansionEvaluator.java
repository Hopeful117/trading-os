package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Deterministic evaluator for the OHLC range-expansion setup (Story 0014):
 *
 * <ul>
 *   <li>|priceChange| &gt;= minimumAbsoluteChange AND rangePercentage &gt;=
 *       minimumRangePercentage → MATCH (direction = sign of priceChange)</li>
 *   <li>inputs present but conditions unmet → NO_MATCH</li>
 *   <li>missing required inputs → NOT_EVALUABLE</li>
 * </ul>
 */
@Component
public class OhlcRangeExpansionEvaluator implements StrategyEvaluator {

    @Override
    public String strategyType() {
        return BuiltinStrategies.OHLC_RANGE_EXPANSION_TYPE;
    }

    @Override
    public boolean supports(StrategyDefinition definition) {
        return BuiltinStrategies.OHLC_RANGE_EXPANSION_ID.equals(definition.strategyId().value())
                && definition.version() == BuiltinStrategies.OHLC_RANGE_EXPANSION_VERSION;
    }

    @Override
    public StrategyEvaluation evaluate(
            StrategyDefinition definition, StrategyEvaluationContext context) {
        if (!context.has(BuiltinStrategies.PRICE_CHANGE)) {
            return StrategyEvaluation.notEvaluable(
                    definition, context, "Required semantic input missing: priceChange");
        }
        if (!context.has(BuiltinStrategies.RANGE_PERCENTAGE)) {
            return StrategyEvaluation.notEvaluable(
                    definition, context, "Required semantic input missing: rangePercentage");
        }
        BigDecimal priceChange = context.get(BuiltinStrategies.PRICE_CHANGE).decimalValue();
        BigDecimal rangePercentage =
                context.get(BuiltinStrategies.RANGE_PERCENTAGE).decimalValue();
        BigDecimal minimumChange = decimalParameter(definition, "minimumAbsoluteChange");
        BigDecimal minimumRange = decimalParameter(definition, "minimumRangePercentage");

        boolean significantMove = priceChange.abs().compareTo(minimumChange) >= 0;
        boolean rangeExpanded = rangePercentage.compareTo(minimumRange) >= 0;

        if (!significantMove || !rangeExpanded) {
            return StrategyEvaluation.noMatch(
                    definition,
                    context,
                    List.of(
                            ConditionResult.of(
                                    BuiltinStrategies.CONDITION_SIGNIFICANT_MOVE,
                                    significantMove, priceChange),
                            ConditionResult.of(
                                    BuiltinStrategies.CONDITION_RANGE_EXPANSION,
                                    rangeExpanded, rangePercentage)),
                    "Range expansion conditions not satisfied",
                    Set.of(BuiltinStrategies.PRICE_CHANGE, BuiltinStrategies.RANGE_PERCENTAGE));
        }

        MatchedDirection direction =
                priceChange.signum() > 0 ? MatchedDirection.LONG : MatchedDirection.SHORT;
        Duration validity = durationParameter(definition, "validityDuration");
        Instant observedAt = context.has(BuiltinStrategies.OBSERVED_AT)
                ? context.get(BuiltinStrategies.OBSERVED_AT).instantValue()
                : context.evaluatedAt();
        Instant validUntil = observedAt.plus(validity);

        return StrategyEvaluation.match(
                definition,
                context,
                direction,
                List.of(
                        ConditionResult.of(
                                BuiltinStrategies.CONDITION_SIGNIFICANT_MOVE, true, priceChange),
                        ConditionResult.of(
                                BuiltinStrategies.CONDITION_RANGE_EXPANSION, true, rangePercentage)),
                BigDecimal.ONE,
                "OHLC range expansion: " + direction.name().toLowerCase()
                        + " within expanded range (" + rangePercentage.toPlainString()
                        + "%), valid until " + validUntil,
                Set.of(BuiltinStrategies.PRICE_CHANGE, BuiltinStrategies.RANGE_PERCENTAGE));
    }

    private static BigDecimal decimalParameter(StrategyDefinition definition, String name) {
        return definition.parameters().find(name)
                .map(StrategyParameter::decimalValue)
                .orElseThrow(() -> new IllegalStateException("parameter missing: " + name));
    }

    private static Duration durationParameter(StrategyDefinition definition, String name) {
        return definition.parameters().find(name)
                .map(StrategyParameter::durationValue)
                .orElse(Duration.ofMinutes(30));
    }
}
