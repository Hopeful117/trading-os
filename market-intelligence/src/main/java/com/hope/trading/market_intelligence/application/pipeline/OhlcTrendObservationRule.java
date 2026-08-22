package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.observation.*;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

final class OhlcTrendObservationRule implements ObservationConsolidationRule {
    static final String VERSION = "ohlc-trend/v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public ObservationRuleResult evaluate(
            String instrument, List<CapabilityExecution> results) {
        CapabilityExecution execution = results.stream()
                .filter(item -> item.capabilityId().value()
                        .equals(OhlcRangeAnalysisCapability.CAPABILITY_ID))
                .filter(item -> item.result().map(result ->
                        result.completeness() == CapabilityCompleteness.COMPLETE).orElse(false))
                .findFirst().orElseThrow(() -> new NoSuchElementException(
                        "No complete OHLC range result is available"));
        CapabilityResult result = execution.result().orElseThrow();
        BigDecimal change = result.metrics().get("priceChange");
        if (change == null) {
            throw new NoSuchElementException("OHLC series contains no price change data");
        }
        DeterministicMeasurements details = result.artifacts().stream()
                .map(ProducedArtifact::artifact).map(item -> item.content())
                .filter(DeterministicMeasurements.class::isInstance)
                .map(DeterministicMeasurements.class::cast).findFirst().orElseThrow();
        if (change.signum() == 0) {
            return new ObservationRuleResult(
                    new ObservationType("PRICE_TREND_NEUTRAL"),
                    "Neutral OHLC trend",
                    "First-to-last OHLC price change is zero.",
                    Set.of("price-action", "deterministic"), "15m",
                    details.observedAt(), details.observedAt().plus(Duration.ofMinutes(30)),
                    List.of(new ObservationEvidenceCandidate(
                            execution.id(), details.title(), details.explanation(),
                            result.metrics(), Map.of("priceChange", BigDecimal.ZERO),
                            details.observedAt(), BigDecimal.ONE)));
        }
        String direction = change.signum() > 0 ? "LONG" : "SHORT";
        return new ObservationRuleResult(
                new ObservationType("PRICE_TREND_" + direction),
                "Directional OHLC trend",
                "First-to-last OHLC price change is " + direction.toLowerCase(Locale.ROOT) + ".",
                Set.of("price-action", "deterministic"), "15m",
                details.observedAt(), details.observedAt().plus(Duration.ofMinutes(30)),
                List.of(new ObservationEvidenceCandidate(
                        execution.id(), details.title(), details.explanation(),
                        result.metrics(), Map.of("priceChange", change),
                        details.observedAt(), BigDecimal.ONE)));
    }
}
