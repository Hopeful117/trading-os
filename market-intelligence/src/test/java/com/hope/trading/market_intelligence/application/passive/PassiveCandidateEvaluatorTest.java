package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisContradictionSeverity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hope.trading.market_intelligence.application.passive.PassiveCandidateFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PassiveCandidateEvaluatorTest {

    @Test
    void qualifyingCandidateIsCurrent() {
        var fixtures = new PassiveCandidateFixtures();

        var projection = fixtures.evaluator().evaluate(List.of(fixtures.opportunity), NOW).getFirst();

        assertThat(projection.current()).isTrue();
        assertThat(projection.diagnostics()).isEmpty();
    }

    @Test
    void supersededThesisIsNotCurrentInFeedAndEvaluation() {
        var fixtures = new PassiveCandidateFixtures();
        when(fixtures.theses.findSupersededThesisIds(any())).thenReturn(Set.of(fixtures.thesisId));

        var single = fixtures.evaluator().evaluateOne(fixtures.opportunity.id(), fixtures.opportunity.version(), NOW)
                .orElseThrow();
        var page = service(fixtures).search(new PassiveAwarenessQuery(null, null, null, false, 0, 20));

        assertThat(single.projection().current()).isFalse();
        assertThat(single.projection().diagnostics()).contains(PassiveCandidateEvaluator.THESIS_SUPERSEDED);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isFalse();
            assertThat(item.diagnostics()).contains(PassiveCandidateEvaluator.THESIS_SUPERSEDED);
        });
    }

    @Test
    void watchAndNoTradeThesesAreNotCurrent() {
        for (MarketThesisState state : List.of(MarketThesisState.WATCH, MarketThesisState.NO_TRADE)) {
            var fixtures = new PassiveCandidateFixtures();
            fixtures.thesis = fixtures.thesis(state, MarketThesisEvidenceRole.SUPPORTING, NOW.minusSeconds(10));
            fixtures.install();

            var projection = fixtures.evaluator().evaluate(List.of(fixtures.opportunity), NOW).getFirst();

            assertThat(projection.current()).as(state.name()).isFalse();
            assertThat(projection.diagnostics()).as(state.name()).contains("THESIS_NOT_CANDIDATE");
        }
    }

    @Test
    void blockingContradictionIsNotCurrent() {
        var fixtures = new PassiveCandidateFixtures();
        fixtures.thesis = fixtures.thesis(MarketThesisState.WATCH, MarketThesisEvidenceRole.CONTRADICTING,
                MarketThesisContradictionSeverity.BLOCKING, NOW.minusSeconds(10));
        fixtures.install();

        var projection = fixtures.evaluator().evaluate(List.of(fixtures.opportunity), NOW).getFirst();

        assertThat(projection.current()).isFalse();
        assertThat(projection.diagnostics()).contains("THESIS_BLOCKING_CONTRADICTION");
    }

    @Test
    void olderOpportunityVersionIsSuperseded() {
        var fixtures = new PassiveCandidateFixtures();
        var older = fixtures.opportunity;
        fixtures.opportunity = fixtures.opportunity(2, OpportunityOrigin.PASSIVE_SCAN, null, NOW.plusSeconds(300));
        fixtures.install();
        when(fixtures.opportunities.find(older.id(), older.version())).thenReturn(Optional.of(older));

        var single = fixtures.evaluator().evaluateOne(older.id(), older.version(), NOW).orElseThrow();

        assertThat(single.projection().current()).isFalse();
        assertThat(single.projection().diagnostics()).contains(PassiveCandidateEvaluator.OPPORTUNITY_SUPERSEDED);
    }

    @Test
    void unknownVersionIsEmpty() {
        var fixtures = new PassiveCandidateFixtures();

        assertThat(fixtures.evaluator().evaluateOne(new OpportunityId(UUID.randomUUID()),
                new OpportunityVersion(1), NOW)).isEmpty();
    }

    @Test
    void marketDataFailureIsReportedAsUnavailableAndNotCurrent() {
        var fixtures = new PassiveCandidateFixtures();
        when(fixtures.marketData.findMarketsByIds(any())).thenThrow(new IllegalStateException("down"));

        var single = fixtures.evaluator().evaluateOne(fixtures.opportunity.id(), fixtures.opportunity.version(), NOW)
                .orElseThrow();

        assertThat(single.marketDataUnavailable()).isTrue();
        assertThat(single.projection().current()).isFalse();
        assertThat(single.projection().diagnostics()).contains(PassiveCandidateEvaluator.MARKET_IDENTITY_UNAVAILABLE);
    }

    @Test
    void repeatedEquivalentDetectionYieldsOneFeedItem() {
        var fixtures = new PassiveCandidateFixtures();
        // Repeated equivalent detections are versions of one opportunity; the latest-version
        // source returns a single row, so the feed shows one item.
        fixtures.opportunity = fixtures.opportunity(3, OpportunityOrigin.PASSIVE_SCAN, null, NOW.plusSeconds(300));
        fixtures.install();

        var page = service(fixtures).search(new PassiveAwarenessQuery(null, null, null, true, 0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().opportunity().version().value()).isEqualTo(3);
    }

    private PassiveAwarenessProjectionService service(PassiveCandidateFixtures fixtures) {
        return new PassiveAwarenessProjectionService(fixtures.opportunities, fixtures.evaluator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
