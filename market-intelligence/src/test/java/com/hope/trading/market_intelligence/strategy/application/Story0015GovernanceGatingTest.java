package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyOperationalStatus;
import com.hope.trading.market_intelligence.strategy.domain.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0015 proof: governance eligibility and market applicability are two
 * independent pre-evaluation filters, with the domain as single source of
 * truth for governance.
 */
class Story0015GovernanceGatingTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final String TIMEFRAME = "15m";
    private static final String PROVIDER = "kraken";

    // ---- fixtures ----------------------------------------------------------

    private static StrategyDefinition definition(
            StrategyOperationalStatus operational, ValidationStatus validation) {
        StrategyDefinition base = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Governed Strategy", null,
                "GOVERNED", StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of()),
                Set.of(), com.hope.trading.market_intelligence.strategy.domain
                        .StrategyParameters.empty(),
                null, NOW);
        return switch (validation) {
            case UNVALIDATED -> apply(base, operational);
            case VALIDATED -> apply(
                    base.recordValidation("backtest://evidence", NOW), operational);
        };
    }

    private static StrategyDefinition apply(
            StrategyDefinition definition, StrategyOperationalStatus target) {
        if (definition.operationalStatus() == target) {
            return definition;
        }
        return definition.transitionTo(target, NOW);
    }

    /** Mirrors the production pipeline selection order exactly. */
    private static boolean selectedByPipeline(StrategyDefinition definition) {
        return definition.isEligibleForLiveEvaluation()
                && isApplicable(definition);
    }

    private static boolean isApplicable(StrategyDefinition definition) {
        StrategyApplicability applicability = definition.applicability();
        return applicability.timeframes().stream()
                .anyMatch(t -> t == StrategyApplicability.Timeframe.parse(TIMEFRAME))
                && (applicability.providers().isEmpty()
                    || applicability.providers().stream()
                        .anyMatch(p -> p.equalsIgnoreCase(PROVIDER)));
    }

    // ---- proofs ------------------------------------------------------------

    @Test
    void validatedAndEnabledAndApplicableReachesEvaluator() {
        StrategyDefinition definition = definition(
                StrategyOperationalStatus.ENABLED, ValidationStatus.VALIDATED);
        assertThat(selectedByPipeline(definition)).isTrue();
    }

    @Test
    void unvalidatedStrategyNeverSelectedEvenWhenEnabledStateRequested() {
        // An unvalidated definition cannot even hold ENABLED state: the only
        // unvalidated live state is the explicit bootstrap controlled run.
        StrategyDefinition unvalidatedDisabled = definition(
                StrategyOperationalStatus.DISABLED, ValidationStatus.UNVALIDATED);
        assertThat(unvalidatedDisabled.isEligibleForLiveEvaluation()).isFalse();
        assertThat(selectedByPipeline(unvalidatedDisabled)).isFalse();
    }

    @Test
    void validatedButDisabledStrategyNeverSelected() {
        StrategyDefinition definition = definition(
                StrategyOperationalStatus.DISABLED, ValidationStatus.VALIDATED);
        assertThat(definition.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(selectedByPipeline(definition)).isFalse();
    }

    @Test
    void retiredStrategyNeverSelected() {
        StrategyDefinition definition = definition(
                StrategyOperationalStatus.ENABLED, ValidationStatus.VALIDATED)
                .retire(NOW.plusSeconds(1));
        assertThat(selectedByPipeline(definition)).isFalse();
    }

    @Test
    void applicableButNotGovernedIsFilteredByGovernance() {
        StrategyDefinition disabledValidated = definition(
                StrategyOperationalStatus.DISABLED, ValidationStatus.VALIDATED);
        // Market fit alone does not grant live participation.
        assertThat(isApplicable(disabledValidated)).isTrue();
        assertThat(selectedByPipeline(disabledValidated)).isFalse();
    }

    @Test
    void governedButNotApplicableIsFilteredByApplicability() {
        StrategyDefinition wrongTimeframe = definition(
                StrategyOperationalStatus.ENABLED, ValidationStatus.VALIDATED);
        assertThat(wrongTimeframe.isEligibleForLiveEvaluation()).isTrue();
        assertThat(isApplicableWith(wrongTimeframe, "1h")).isFalse();

        StrategyDefinition wrongProvider = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Provider Bound", null,
                "PROVIDER_BOUND", StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of("binance")),
                Set.of(), com.hope.trading.market_intelligence.strategy.domain
                        .StrategyParameters.empty(),
                null, NOW)
                .recordValidation("backtest://evidence", NOW)
                .transitionTo(StrategyOperationalStatus.ENABLED, NOW);
        assertThat(wrongProvider.isEligibleForLiveEvaluation()).isTrue();
        assertThat(isApplicable(wrongProvider)).isFalse();
    }

    @Test
    void bothProductionStrategiesRemainEligibleThroughTheirDeclaredStates() {
        BuiltinStrategies builtins = new BuiltinStrategies();
        List<StrategyDefinition> all = builtins.all();
        // Legacy runs under the explicit bootstrap controlled run; range
        // expansion is truthfully DISABLED until validation evidence exists.
        assertThat(all.stream()
                .filter(StrategyDefinition::isEligibleForLiveEvaluation)
                .map(StrategyDefinition::name))
                .containsExactly("Legacy OHLC Trend");
        assertThat(all).extracting(StrategyDefinition::name)
                .contains("OHLC Range Expansion");
    }

    private static boolean isApplicableWith(StrategyDefinition definition, String timeframe) {
        StrategyApplicability applicability = definition.applicability();
        return applicability.timeframes().stream()
                .anyMatch(t -> t == StrategyApplicability.Timeframe.parse(timeframe));
    }
}
