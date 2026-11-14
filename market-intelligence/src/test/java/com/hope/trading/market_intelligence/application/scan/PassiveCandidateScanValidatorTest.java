package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.passive.PassiveCandidateFixtures;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.strategy.ActiveAnalysisStrategy;
import com.hope.trading.market_intelligence.application.strategy.PassiveAnalysisStrategy;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.scan.PassiveCandidateProvenance;
import com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference;
import com.hope.trading.market_intelligence.domain.scan.PassiveEvidenceAssessment.Dimension;
import com.hope.trading.market_intelligence.domain.scan.PassiveEvidenceAssessment.Status;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hope.trading.market_intelligence.application.passive.PassiveCandidateFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassiveCandidateScanValidatorTest {
    private final PassiveCandidateFixtures fixtures = new PassiveCandidateFixtures();
    private final AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);

    @Test
    void validCandidateProducesCompleteImmutableProvenance() {
        passiveExecution(EvidenceAcquisitionPolicy.LIVE_CURRENT);

        PassiveCandidateProvenance provenance = validator().validate(reference(), command(List.of(fixtures.marketId),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED), NOW);

        assertThat(provenance.opportunityId()).isEqualTo(fixtures.opportunity.id().value());
        assertThat(provenance.opportunityVersion()).isEqualTo(1);
        assertThat(provenance.strategyMatchId()).isEqualTo(fixtures.strategyMatchId);
        assertThat(provenance.observationIds()).containsExactly(fixtures.observationId);
        assertThat(provenance.thesisId()).isEqualTo(fixtures.thesisId);
        assertThat(provenance.thesisVersion()).isEqualTo(1);
        assertThat(provenance.passiveScanRunId()).isEqualTo(fixtures.passiveRunId);
        assertThat(provenance.passiveAnalysisExecutionId()).isEqualTo(fixtures.passiveExecutionId);
        assertThat(provenance.marketId()).isEqualTo(fixtures.marketId);
        assertThat(provenance.provider()).isEqualTo("KRAKEN");
        assertThat(provenance.productType()).isEqualTo("SPOT");
        assertThat(provenance.validatedAt()).isEqualTo(NOW);
        assertThat(provenance.thesisSourceCutoff()).isEqualTo(NOW.minusSeconds(10));
    }

    @Test
    void evidenceAssessmentReferencesPassiveEvidenceButReacquiresMissingCapabilities() {
        passiveExecution(EvidenceAcquisitionPolicy.LIVE_CURRENT);

        PassiveCandidateProvenance provenance = validator().validate(reference(), command(List.of(fixtures.marketId),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.FOREX_QUOTE_BASIC), NOW);

        assertThat(provenance.evidenceAssessment()).extracting(value -> value.dimension())
                .containsExactly(Dimension.EVIDENCE_POLICY, Dimension.CAPABILITIES, Dimension.CUTOFF,
                        Dimension.FRESHNESS);
        assertThat(provenance.evidenceAssessment().get(0).status()).isEqualTo(Status.COMPATIBLE_REFERENCE_ONLY);
        assertThat(provenance.evidenceAssessment().get(1).status()).isEqualTo(Status.INCOMPATIBLE_REACQUIRED);
        assertThat(provenance.evidenceAssessment().get(1).reason()).startsWith("MISSING:");
        assertThat(provenance.evidenceAssessment().get(2).reason()).isEqualTo("PASSIVE_CUTOFF_UNDECLARED");
        assertThat(provenance.evidenceAssessment().get(3).status()).isEqualTo(Status.COMPATIBLE_REFERENCE_ONLY);
    }

    @Test
    void staleSourceCutoffAndMissingPassiveExecutionAreRecordedAsReacquired() {
        fixtures.thesis = fixtures.thesis(MarketThesisState.CANDIDATE, MarketThesisEvidenceRole.SUPPORTING,
                NOW.minusSeconds(120));
        fixtures.install();
        when(executions.findById(any())).thenReturn(Optional.empty());

        PassiveCandidateProvenance provenance = validator().validate(reference(), command(List.of(fixtures.marketId),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED), NOW);

        assertThat(provenance.evidenceAssessment()).allSatisfy(value ->
                assertThat(value.status()).isEqualTo(Status.INCOMPATIBLE_REACQUIRED));
        assertThat(provenance.evidenceAssessment().get(0).reason()).isEqualTo("PASSIVE_EXECUTION_UNAVAILABLE");
        assertThat(provenance.evidenceAssessment().get(3).reason()).isEqualTo("OLDER_THAN_PT30S");
    }

    @Test
    void declaredRecentCutoffIsCompatibleAndStrictObservedPolicyIsReacquired() {
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.provenance()).thenReturn(new AnalysisExecutionProvenance(
                fixtures.marketId, AnalysisExecutionMode.PASSIVE, "PASSIVE_SCAN", "v1", null, null, null,
                NOW.minusSeconds(5), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                TradeabilityEvidenceProfile.UNSUPPORTED));
        when(executions.findById(fixtures.passiveExecutionId)).thenReturn(Optional.of(execution));

        PassiveCandidateProvenance provenance = validator().validate(reference(), selected(), NOW);

        assertThat(provenance.evidenceAssessment().get(0).status()).isEqualTo(Status.INCOMPATIBLE_REACQUIRED);
        assertThat(provenance.evidenceAssessment().get(0).reason()).isEqualTo("PASSIVE_POLICY_STRICT_OBSERVED");
        assertThat(provenance.evidenceAssessment().get(2).status()).isEqualTo(Status.COMPATIBLE_REFERENCE_ONLY);
        assertThat(provenance.evidenceAssessment().get(2).reason()).isEqualTo("WITHIN_PT30S");
    }

    @Test
    void unknownCandidateIsNotFound() {
        assertFailure(new PassiveCandidateReference(UUID.randomUUID(), 1), selected(), "PASSIVE_CANDIDATE_NOT_FOUND", 404);
    }

    @Test
    void nonPassiveOrAccountBoundOpportunityIsRejected() {
        fixtures.opportunity = fixtures.opportunity(1, OpportunityOrigin.ACTIVE_SCAN, null, NOW.plusSeconds(300));
        fixtures.install();
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_INVALID_ORIGIN", 422);

        fixtures.opportunity = fixtures.opportunity(1, OpportunityOrigin.PASSIVE_SCAN, UUID.randomUUID(),
                NOW.plusSeconds(300));
        fixtures.install();
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_INVALID_ORIGIN", 422);
    }

    @Test
    void scopeMustBeExactlyTheCandidateMarket() {
        assertFailure(reference(), command(List.of(UUID.randomUUID()), MarketScopeMode.SELECTED,
                TradeabilityEvidenceProfile.UNSUPPORTED), "PASSIVE_CANDIDATE_MARKET_MISMATCH", 422);
        assertFailure(reference(), command(List.of(fixtures.marketId, UUID.randomUUID()), MarketScopeMode.SELECTED,
                TradeabilityEvidenceProfile.UNSUPPORTED), "PASSIVE_CANDIDATE_MARKET_MISMATCH", 422);
        assertFailure(reference(), command(List.of(), MarketScopeMode.ALL_ELIGIBLE,
                TradeabilityEvidenceProfile.UNSUPPORTED), "PASSIVE_CANDIDATE_MARKET_MISMATCH", 422);
    }

    @Test
    void supersededOpportunityVersionOrThesisIsRejected() {
        var original = fixtures.opportunity;
        fixtures.opportunity = fixtures.opportunity(2, OpportunityOrigin.PASSIVE_SCAN, null, NOW.plusSeconds(300));
        fixtures.install();
        when(fixtures.opportunities.find(original.id(), original.version())).thenReturn(Optional.of(original));
        assertFailure(new PassiveCandidateReference(original.id().value(), 1), selected(),
                "PASSIVE_CANDIDATE_SUPERSEDED", 409);

        var thesisFixtures = new PassiveCandidateFixtures();
        when(thesisFixtures.theses.findSupersededThesisIds(any())).thenReturn(Set.of(thesisFixtures.thesisId));
        assertThatThrownBy(() -> validator(thesisFixtures).validate(
                new PassiveCandidateReference(thesisFixtures.opportunity.id().value(), 1),
                new CreateActiveScanCommand(UUID.randomUUID(), "key", UUID.randomUUID(), null, "investigate",
                        List.of(thesisFixtures.marketId), MarketScopeMode.SELECTED,
                        TradeabilityEvidenceProfile.UNSUPPORTED, thesisFixtures.opportunity.id().value(), 1L), NOW))
                .isInstanceOfSatisfying(ActiveScanException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PASSIVE_CANDIDATE_SUPERSEDED"));
    }

    @Test
    void expiredOrNonCandidateEvidenceIsNotCurrent() {
        fixtures.opportunity = fixtures.opportunity(1, OpportunityOrigin.PASSIVE_SCAN, null, NOW.minusSeconds(1));
        fixtures.install();
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_NOT_CURRENT", 409);

        var watch = new PassiveCandidateFixtures();
        watch.thesis = watch.thesis(MarketThesisState.WATCH, MarketThesisEvidenceRole.SUPPORTING, NOW.minusSeconds(10));
        watch.install();
        assertThatThrownBy(() -> validator(watch).validate(
                new PassiveCandidateReference(watch.opportunity.id().value(), 1),
                new CreateActiveScanCommand(UUID.randomUUID(), "key", UUID.randomUUID(), null, "investigate",
                        List.of(watch.marketId), MarketScopeMode.SELECTED,
                        TradeabilityEvidenceProfile.UNSUPPORTED, watch.opportunity.id().value(), 1L), NOW))
                .isInstanceOfSatisfying(ActiveScanException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("PASSIVE_CANDIDATE_NOT_CURRENT");
                    assertThat(failure.getMessage()).contains("THESIS_NOT_CANDIDATE");
                });
    }

    @Test
    void missingReferencesAreNotCurrent() {
        when(fixtures.strategyMatches.findByIds(any())).thenReturn(List.of());
        when(fixtures.observations.findByIds(any())).thenReturn(List.of());
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_NOT_CURRENT", 409);
    }

    @Test
    void providerIdentityMismatchIsNotCurrent() {
        when(fixtures.marketData.findMarketsByIds(any())).thenReturn(List.of(
                new com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse(
                        fixtures.marketId, "CTRADER", "EURUSD", "EUR", "USD", "CFD", null)));
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_NOT_CURRENT", 409);
    }

    @Test
    void marketDataUnavailableFailsClosed() {
        when(fixtures.marketData.findMarketsByIds(any())).thenThrow(new IllegalStateException("down"));
        assertFailure(reference(), selected(), "PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE", 503);
    }

    private void assertFailure(PassiveCandidateReference reference, CreateActiveScanCommand command,
                               String code, int status) {
        assertThatThrownBy(() -> validator().validate(reference, command, NOW))
                .isInstanceOfSatisfying(ActiveScanException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(code);
                    assertThat(failure.status()).isEqualTo(status);
                });
    }

    private void passiveExecution(EvidenceAcquisitionPolicy policy) {
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.provenance()).thenReturn(new AnalysisExecutionProvenance(
                fixtures.marketId, AnalysisExecutionMode.PASSIVE, "PASSIVE_SCAN", "v1", null, null, null,
                null, policy, TradeabilityEvidenceProfile.UNSUPPORTED));
        when(executions.findById(fixtures.passiveExecutionId)).thenReturn(Optional.of(execution));
    }

    private PassiveCandidateReference reference() {
        return new PassiveCandidateReference(fixtures.opportunity.id().value(), fixtures.opportunity.version().value());
    }

    private CreateActiveScanCommand selected() {
        return command(List.of(fixtures.marketId), MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED);
    }

    private CreateActiveScanCommand command(List<UUID> markets, MarketScopeMode mode,
                                            TradeabilityEvidenceProfile profile) {
        return new CreateActiveScanCommand(UUID.randomUUID(), "key", UUID.randomUUID(), null, "investigate",
                markets, mode, profile, fixtures.opportunity.id().value(), fixtures.opportunity.version().value());
    }

    private PassiveCandidateScanValidator validator() {
        return validator(fixtures);
    }

    private PassiveCandidateScanValidator validator(PassiveCandidateFixtures source) {
        return new PassiveCandidateScanValidator(source.evaluator(), executions,
                new PassiveAnalysisStrategy(Duration.ofMillis(750)), new ActiveAnalysisStrategy(Duration.ofSeconds(3)));
    }
}
