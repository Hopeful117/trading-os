package com.hope.trading.market_intelligence.strategy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyDefinitionTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final UUID STRATEGY_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static StrategyDefinition draft() {
        return StrategyDefinition.create(
                new StrategyId(STRATEGY_UUID),
                1,
                "OHLC Trend",
                "Legacy bootstrap trend strategy",
                StrategyDirection.DYNAMIC,
                applicability(),
                Set.of(new RequiredSemanticInput(SemanticInputType.OBSERVATION, "PRICE_TREND")),
                new StrategyParameters(List.of(
                        new StrategyParameter("lookback",
                                StrategyParameter.ParameterType.INTEGER, 20L))),
                "research://hypothesis/1",
                NOW);
    }

    private static StrategyApplicability applicability() {
        return new StrategyApplicability(
                Set.of("CRYPTO"), Set.of(StrategyApplicability.Timeframe.M15), Set.of());
    }

    @Test
    void createdDefinitionStartsAsUnvalidatedDraft() {
        StrategyDefinition definition = draft();
        assertThat(definition.lifecycle()).isEqualTo(StrategyLifecycle.DRAFT);
        assertThat(definition.validationStatus()).isEqualTo(ValidationStatus.UNVALIDATED);
        assertThat(definition.validationEvidenceRef()).isNull();
        assertThat(definition.createdAt()).isEqualTo(NOW);
        assertThat(definition.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void exactVersionIdentityIsStrategyIdAndVersionOnly() {
        StrategyDefinition first = draft();
        StrategyDefinition same = draft();
        StrategyDefinition otherVersion = draft().deriveVersion(2, NOW);
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(otherVersion);
        assertThat(otherVersion.version()).isEqualTo(2);
        assertThat(otherVersion.strategyId()).isEqualTo(first.strategyId());
    }

    @Test
    void versionMustBePositive() {
        assertThatThrownBy(() -> StrategyDefinition.create(
                StrategyId.random(), 0, "x", null, StrategyDirection.LONG,
                applicability(), Set.of(), StrategyParameters.empty(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivedVersionsCopySemanticsAndResetGovernance() {
        StrategyDefinition v1 = draft().recordValidation("backtest://bt-1", LATER);
        StrategyDefinition v2 = v1.deriveVersion(2, LATER);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.lifecycle()).isEqualTo(StrategyLifecycle.DRAFT);
        assertThat(v2.validationStatus()).isEqualTo(ValidationStatus.UNVALIDATED);
        assertThat(v2.requiredInputs()).isEqualTo(v1.requiredInputs());
        assertThat(v2.parameters()).isEqualTo(v1.parameters());
        assertThat(v2.applicability()).isEqualTo(v1.applicability());
        assertThat(v2.direction()).isEqualTo(v1.direction());
    }

    @Test
    void derivedVersionMustIncrease() {
        assertThatThrownBy(() -> draft().deriveVersion(1, LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legalTransitionsFollowAdr034Chain() {
        StrategyDefinition definition = draft();
        definition = definition.transitionTo(StrategyLifecycle.CANDIDATE, LATER);
        definition = definition.recordValidation("backtest://evidence-1", LATER)
                .transitionTo(StrategyLifecycle.VALIDATED, LATER);
        definition = definition.transitionTo(StrategyLifecycle.ENABLED, LATER);
        definition = definition.retire(LATER);
        assertThat(definition.lifecycle()).isEqualTo(StrategyLifecycle.RETIRED);
    }

    @Test
    void illegalTransitionsAreRejected() {
        StrategyDefinition fresh = draft();
        assertThatThrownBy(() -> fresh.transitionTo(StrategyLifecycle.ENABLED, LATER))
                .isInstanceOf(IllegalStrategyTransitionException.class);
        StrategyDefinition candidate = fresh.transitionTo(StrategyLifecycle.CANDIDATE, LATER);
        assertThatThrownBy(() -> candidate.transitionTo(StrategyLifecycle.ENABLED, LATER))
                .isInstanceOf(IllegalStrategyTransitionException.class);
        StrategyDefinition enabled = candidate
                .recordValidation("backtest://evidence", LATER)
                .transitionTo(StrategyLifecycle.VALIDATED, LATER)
                .transitionTo(StrategyLifecycle.ENABLED, LATER);
        assertThatThrownBy(() -> enabled.retire(LATER).retire(LATER.plusSeconds(1)))
                .isInstanceOf(IllegalStrategyTransitionException.class);
    }

    @Test
    void retiredIsTerminal() {
        StrategyDefinition retired = draft().retire(LATER);
        assertThat(retired.lifecycle().isTerminal()).isTrue();
        assertThatThrownBy(() -> retired.transitionTo(StrategyLifecycle.DRAFT, LATER.plusSeconds(1)))
                .isInstanceOf(IllegalStrategyTransitionException.class);
        assertThatThrownBy(() -> retired.recordValidation("backtest://x", LATER.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatedLifecycleRequiresAcceptedEvidence() {
        StrategyDefinition candidate = draft().transitionTo(StrategyLifecycle.CANDIDATE, LATER);
        StrategyDefinition withEvidence =
                candidate.recordValidation("backtest://evidence", LATER);
        StrategyDefinition validated =
                withEvidence.transitionTo(StrategyLifecycle.VALIDATED, LATER.plusSeconds(1));
        assertThat(validated.lifecycle()).isEqualTo(StrategyLifecycle.VALIDATED);
        assertThat(validated.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(validated.validationEvidenceRef()).isEqualTo("backtest://evidence");

        // a governance VALIDATED lifecycle is impossible while evidence is absent
        StrategyDefinition unvalidatedCandidate =
                StrategyDefinition.create(
                        StrategyId.random(), 1, "n", null, StrategyDirection.LONG,
                        applicability(), Set.of(), StrategyParameters.empty(), null, NOW)
                        .transitionTo(StrategyLifecycle.CANDIDATE, NOW);

        // recordValidation then removing evidence is impossible (immutable),
        // so simulate the missing-evidence case through an unrecorded candidate:
        // transition must fail because validationStatus is still UNVALIDATED.
        assertThatThrownBy(() -> unvalidatedCandidate.transitionTo(
                StrategyLifecycle.VALIDATED, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordValidationRequiresEvidenceReference() {
        assertThatThrownBy(() -> draft().recordValidation(" ", LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parametersExposeTypedValuesDeterministically() {
        StrategyParameters parameters = new StrategyParameters(List.of(
                new StrategyParameter("minimumMomentum",
                        StrategyParameter.ParameterType.DECIMAL, new BigDecimal("0.015")),
                new StrategyParameter("window",
                        StrategyParameter.ParameterType.DURATION, Duration.ofMinutes(30)),
                new StrategyParameter("label", StrategyParameter.ParameterType.STRING, "trend")));
        assertThat(parameters.find("minimumMomentum").orElseThrow().decimalValue())
                .isEqualByComparingTo("0.015");
        assertThat(parameters.find("window").orElseThrow().durationValue())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(parameters.find("label").orElseThrow().stringValue()).isEqualTo("trend");
    }

    @Test
    void parametersRejectBadValuesDuplicatesAndBlankNames() {
        assertThatThrownBy(() -> new StrategyParameter("p",
                StrategyParameter.ParameterType.DECIMAL, "not-a-number"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StrategyParameters(List.of(
                new StrategyParameter("p", StrategyParameter.ParameterType.STRING, "a"),
                new StrategyParameter("p", StrategyParameter.ParameterType.STRING, "b"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StrategyParameter(" ",
                StrategyParameter.ParameterType.STRING, "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicabilityNormalizesAndRequiresCoreDimensions() {
        StrategyApplicability applicability = new StrategyApplicability(
                Set.of("crypto"), Set.of(StrategyApplicability.Timeframe.M15), Set.of("kraken"));
        assertThat(applicability.assetClasses()).containsExactly("CRYPTO");
        assertThat(applicability.providers()).containsExactly("KRAKEN");
        assertThatThrownBy(() -> new StrategyApplicability(
                Set.of(), Set.of(StrategyApplicability.Timeframe.M15), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StrategyApplicability(
                Set.of("CRYPTO"), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredInputsNormalizeKeysAndRejectBlanks() {
        RequiredSemanticInput input =
                new RequiredSemanticInput(SemanticInputType.FEATURE, "spread");
        assertThat(input.toString()).isEqualTo("FEATURE:SPREAD");
        assertThatThrownBy(() -> new RequiredSemanticInput(SemanticInputType.FEATURE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
