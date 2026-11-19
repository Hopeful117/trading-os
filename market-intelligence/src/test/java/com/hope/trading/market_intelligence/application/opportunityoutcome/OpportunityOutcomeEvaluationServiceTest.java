package com.hope.trading.market_intelligence.application.opportunityoutcome;

import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.OpportunityOutcomeRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpportunityOutcomeEvaluationServiceTest {
    private static final UUID OPPORTUNITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MARKET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant CUTOFF = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void expiredOpportunityIsCapturedWithoutMarketLookAhead() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var opportunity = opportunity(1, OpportunityStatus.EXPIRED, CUTOFF);
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(opportunity));
        when(opportunities.findLatest(new OpportunityId(OPPORTUNITY_ID))).thenReturn(Optional.of(opportunity));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3600))
                .evaluate(request(1, CUTOFF.plusSeconds(600)));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.EXPIRED);
        verifyNoInteractions(marketOutcomes);
    }

    @Test
    void newerVersionIsCapturedAsSupersededOnlyWhenKnownAtCutoff() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var original = opportunity(1, OpportunityStatus.ACTIVE, CUTOFF);
        var newer = opportunity(2, OpportunityStatus.ACTIVE, CUTOFF.plusSeconds(1));
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(original));
        when(opportunities.findLatest(new OpportunityId(OPPORTUNITY_ID))).thenReturn(Optional.of(newer));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3610))
                .evaluate(request(1, CUTOFF.plusSeconds(10)));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.SUPERSEDED);
        verifyNoInteractions(marketOutcomes);
    }

    @Test
    void incompleteMarketEvidenceIsNonEvaluable() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var opportunity = opportunity(1, OpportunityStatus.ACTIVE, CUTOFF);
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(opportunity));
        when(opportunities.findLatest(new OpportunityId(OPPORTUNITY_ID))).thenReturn(Optional.of(opportunity));
        when(marketOutcomes.evaluate(any())).thenReturn(marketOutcome(ObservedMarketOutcomeStatus.INCOMPLETE));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(evidence.validate(any(), any())).thenReturn(new OpportunityEvidenceAdmissibility.Decision(true, null, null));
        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3600))
                .evaluate(request(1, CUTOFF));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.NON_EVALUABLE);
        assertThat(result.reason()).isEqualTo("MARKET_OUTCOME_INCOMPLETE");
    }

    @Test
    void invalidatedOpportunityIsCapturedWithoutMarketLookAhead() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var opportunity = opportunity(1, OpportunityStatus.INVALIDATED, CUTOFF);
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(opportunity));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3600))
                .evaluate(request(1, CUTOFF));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.INVALIDATED);
        verifyNoInteractions(marketOutcomes);
    }

    @Test
    void unavailableMarketEvidenceIsUnknown() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var opportunity = opportunity(1, OpportunityStatus.ACTIVE, CUTOFF);
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(opportunity));
        when(opportunities.findLatest(new OpportunityId(OPPORTUNITY_ID))).thenReturn(Optional.of(opportunity));
        when(evidence.validate(any(), any())).thenReturn(new OpportunityEvidenceAdmissibility.Decision(true, null, null));
        when(marketOutcomes.evaluate(any())).thenReturn(marketOutcome(ObservedMarketOutcomeStatus.UNAVAILABLE));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3600))
                .evaluate(request(1, CUTOFF));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.UNKNOWN);
        assertThat(result.reason()).isEqualTo("MARKET_OUTCOME_UNAVAILABLE");
    }

    @Test
    void missingMarketIdentityIsPersistedAsNonEvaluable() {
        TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
        MarketOutcomeEvaluationService marketOutcomes = mock(MarketOutcomeEvaluationService.class);
        OpportunityOutcomeRepository outcomes = mock(OpportunityOutcomeRepository.class);
        OpportunityEvidenceAdmissibility evidence = mock(OpportunityEvidenceAdmissibility.class);
        var opportunity = opportunityWithoutMarket(1, OpportunityStatus.ACTIVE, CUTOFF);
        when(opportunities.find(new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(1)))
                .thenReturn(Optional.of(opportunity));
        when(opportunities.findLatest(new OpportunityId(OPPORTUNITY_ID))).thenReturn(Optional.of(opportunity));
        when(evidence.validate(any(), any())).thenReturn(new OpportunityEvidenceAdmissibility.Decision(true, null, null));
        when(outcomes.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(opportunities, marketOutcomes, outcomes, evidence, CUTOFF.plusSeconds(3600))
                .evaluate(request(1, CUTOFF));

        assertThat(result.status()).isEqualTo(com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.NON_EVALUABLE);
        assertThat(result.reason()).isEqualTo("MARKET_IDENTITY_UNAVAILABLE");
        assertThat(result.marketId()).isNull();
        verifyNoInteractions(marketOutcomes);
    }

    private OpportunityOutcomeEvaluationService service(
            TradingOpportunityRepository opportunities,
            MarketOutcomeEvaluationService marketOutcomes,
            OpportunityOutcomeRepository outcomes,
            OpportunityEvidenceAdmissibility evidence,
            Instant now) {
        return new OpportunityOutcomeEvaluationService(opportunities, marketOutcomes, outcomes, evidence,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private OpportunityOutcomeEvaluationRequest request(long version, Instant cutoff) {
        return new OpportunityOutcomeEvaluationRequest(new OpportunityId(OPPORTUNITY_ID),
                new OpportunityVersion(version), cutoff, OutcomeHorizon.oneHour(), null);
    }

    private com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunity(
            long version, OpportunityStatus status, Instant evaluatedAt) {
        return new com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory().create(
                new com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory.Values(
                        new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(version), status,
                        "BTC/EUR", com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection.LONG,
                        "Bullish breakout", "5m", com.hope.trading.market_intelligence.domain.opportunity.OpportunityType.SCALPING,
                        com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin.PASSIVE_SCAN,
                        new OpportunityScore(new BigDecimal("50")), "Confirmed",
                        java.util.Set.of(new com.hope.trading.market_intelligence.domain.opportunity.ObservationReference(UUID.randomUUID())),
                        java.util.Set.of(), evaluatedAt, evaluatedAt, evaluatedAt.plusSeconds(300), evaluatedAt,
                         null, null, MARKET_ID, null, null, null, null, null));
    }

    private com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunityWithoutMarket(
            long version, OpportunityStatus status, Instant evaluatedAt) {
        return new com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory().create(
                new com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory.Values(
                        new OpportunityId(OPPORTUNITY_ID), new OpportunityVersion(version), status,
                        "BTC/EUR", com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection.LONG,
                        "Bullish breakout", "5m", com.hope.trading.market_intelligence.domain.opportunity.OpportunityType.SCALPING,
                        com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin.PASSIVE_SCAN,
                        new OpportunityScore(new BigDecimal("50")), "Confirmed",
                        java.util.Set.of(new com.hope.trading.market_intelligence.domain.opportunity.ObservationReference(UUID.randomUUID())),
                        java.util.Set.of(), evaluatedAt, evaluatedAt, evaluatedAt.plusSeconds(300), evaluatedAt,
                        null, null, null, null, null, null, null, null));
    }

    private ObservedMarketOutcome marketOutcome(ObservedMarketOutcomeStatus status) {
        return new ObservedMarketOutcome(MARKET_ID, CUTOFF, OutcomeHorizon.oneHour(),
                CUTOFF.plusSeconds(3600), status, "INCOMPLETE", null, null, null, null, null,
                null, null, null, null, 4, 3, null, java.util.List.of(), null,
                "market-outcome-v1", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                CUTOFF.plusSeconds(3600), null);
    }
}
