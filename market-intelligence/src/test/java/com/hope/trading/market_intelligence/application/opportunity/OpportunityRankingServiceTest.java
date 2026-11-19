package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.evidencequality.EvidenceQualityEvaluator;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityVersionRef;
import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.thesis.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class OpportunityRankingServiceTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void resolvesExactThesisAndReportsTerminalAndUnassessedCandidates() {
        MarketThesis exact = thesisWithQuality();
        MarketThesisReference reference = new MarketThesisReference(exact.id(), exact.version());
        TradingOpportunity rankable = opportunity(
                "11111111-1111-1111-1111-111111111111", OpportunityStatus.ACTIVE, reference);
        TradingOpportunity terminal = opportunity(
                "22222222-2222-2222-2222-222222222222", OpportunityStatus.EXPIRED, reference);
        TradingOpportunity missingThesis = opportunity(
                "33333333-3333-3333-3333-333333333333", OpportunityStatus.ACTIVE, null);

        FakeOpportunityRepository opportunities = new FakeOpportunityRepository(
                List.of(missingThesis, terminal, rankable));
        FakeThesisRepository theses = new FakeThesisRepository(exact);
        OpportunityRankingService service = new OpportunityRankingService(opportunities, theses,
                new OpportunityRankingEngine(), new EvidenceBasedOpportunityRankingPolicy());

        OpportunityRankingResult result = service.rankLatest();

        assertThat(result.ranked()).extracting(value -> value.candidate().opportunity().id().value())
                .containsExactly(rankable.id().value());
        assertThat(result.excluded()).extracting(OpportunityRankingResult.ExcludedOpportunity::code)
                .containsExactly("TERMINAL_OPPORTUNITY", "MISSING_EXACT_EVIDENCE_REFERENCE");
        assertThat(theses.requested).containsExactly(
                new com.hope.trading.market_intelligence.application.port.MarketThesisReference(
                        exact.id(), exact.version()));

        OpportunityRankingResult reordered = new OpportunityRankingService(
                new FakeOpportunityRepository(List.of(rankable, terminal, missingThesis)), theses,
                new OpportunityRankingEngine(), new EvidenceBasedOpportunityRankingPolicy()).rankLatest();
        assertThat(reordered.excluded()).isEqualTo(result.excluded());
    }

    @Test
    void excludesRepositoryResultWhenItsIdentityDoesNotMatchTheExactReference() {
        MarketThesis returned = thesisWithQuality();
        MarketThesisReference requested = new MarketThesisReference(
                UUID.randomUUID(), returned.version() + 1);
        TradingOpportunity opportunity = opportunity(
                "44444444-4444-4444-4444-444444444444", OpportunityStatus.ACTIVE, requested);
        FakeOpportunityRepository opportunities = new FakeOpportunityRepository(List.of(opportunity));
        FakeThesisRepository theses = new FakeThesisRepository(returned, true);
        OpportunityRankingService service = new OpportunityRankingService(opportunities, theses,
                new OpportunityRankingEngine(), new EvidenceBasedOpportunityRankingPolicy());

        OpportunityRankingResult result = service.rankLatest();

        assertThat(result.ranked()).isEmpty();
        assertThat(result.excluded()).singleElement().satisfies(excluded -> {
            assertThat(excluded.code()).isEqualTo("MISMATCHED_EXACT_EVIDENCE_REFERENCE");
            assertThat(excluded.opportunityId()).isEqualTo(opportunity.id().value());
        });
    }

    private static TradingOpportunity opportunity(String id, OpportunityStatus status,
                                                  MarketThesisReference thesis) {
        return OpportunityTestFixtures.opportunity(new OpportunityId(UUID.fromString(id)), 1, status,
                new OpportunityScore(BigDecimal.TEN), CUTOFF, thesis);
    }

    private static MarketThesis thesisWithQuality() {
        MarketThesis raw = new MarketThesis(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
                "BTC/EUR", MarketThesisState.CANDIDATE,
                new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "test", "confirm", "invalidate", "level"),
                List.of(new MarketThesisEvidence(UUID.randomUUID(), MarketThesisEvidenceRole.SUPPORTING,
                        MarketThesisAvailability.AVAILABLE, null, "TEST", "reference", "H1",
                        "test evidence", CUTOFF, CUTOFF.plusSeconds(1800), "provider", "BTC/EUR",
                        "source-fingerprint", "rule-v1", CUTOFF, true)),
                List.of("test"), CUTOFF, CUTOFF, CUTOFF, CUTOFF.plusSeconds(1800), UUID.randomUUID(),
                "composer-v1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);
        return new MarketThesis(raw.id(), raw.lineageId(), raw.version(), raw.marketId(), raw.instrument(),
                raw.state(), raw.scenario().orElseThrow(), raw.evidence(), raw.reasons(), raw.evaluatedAt(),
                raw.sourceCutoff(), raw.validFrom(), raw.validUntil().orElseThrow(),
                raw.analysisExecutionId().orElse(null), raw.composerVersion(), raw.inputFingerprint(),
                raw.supersedes().orElse(null), new EvidenceQualityEvaluator().assess(raw));
    }

    private static final class FakeOpportunityRepository implements TradingOpportunityRepository {
        private final List<TradingOpportunity> values;

        private FakeOpportunityRepository(List<TradingOpportunity> values) {
            this.values = values;
        }

        @Override public TradingOpportunity append(TradingOpportunity opportunity) { return opportunity; }
        @Override public Optional<TradingOpportunity> find(OpportunityId id, OpportunityVersion version) {
            return Optional.empty();
        }
        @Override public Optional<TradingOpportunity> findLatest(OpportunityId id) { return Optional.empty(); }
        @Override public List<TradingOpportunity> findActive() { return List.of(); }
        @Override public List<TradingOpportunity> findHistory(OpportunityId id) { return List.of(); }
        @Override public List<TradingOpportunity> findEquivalentCandidates(String instrument,
                OpportunityDirection direction, String scenario, String timeframe, Instant evaluatedAfter) {
            return List.of();
        }
        @Override public List<TradingOpportunity> findAllLatest() { return values; }
        @Override public List<TradingOpportunity> findLatestPassive(int limit) { return List.of(); }
        @Override public List<TradingOpportunity> findAllExact(Collection<TradingOpportunityVersionRef> refs) {
            return List.of();
        }
        @Override public List<TradingOpportunity> findByStrategyMatchIds(Collection<UUID> strategyMatchIds) {
            return List.of();
        }
    }

    private static final class FakeThesisRepository implements MarketThesisRepository {
        private final MarketThesis thesis;
        private final boolean ignoreRequestedIdentity;
        private final List<com.hope.trading.market_intelligence.application.port.MarketThesisReference> requested
                = new ArrayList<>();

        private FakeThesisRepository(MarketThesis thesis) {
            this(thesis, false);
        }

        private FakeThesisRepository(MarketThesis thesis, boolean ignoreRequestedIdentity) {
            this.thesis = thesis;
            this.ignoreRequestedIdentity = ignoreRequestedIdentity;
        }

        @Override public MarketThesis save(MarketThesis value) { return value; }
        @Override public MarketThesis saveIfAbsent(MarketThesis value) { return value; }
        @Override public Optional<MarketThesis> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<MarketThesis> findByIdAndVersion(UUID id, long version) {
            requested.add(new com.hope.trading.market_intelligence.application.port.MarketThesisReference(
                    id, version));
            return ignoreRequestedIdentity || thesis.id().equals(id) && thesis.version() == version
                    ? Optional.of(thesis) : Optional.empty();
        }
        @Override public Set<UUID> findSupersededThesisIds(Collection<UUID> thesisIds) { return Set.of(); }
        @Override public Optional<MarketThesis> findByInputFingerprint(String inputFingerprint) {
            return Optional.empty();
        }
        @Override public Optional<MarketThesis> findLatest(UUID marketId, String instrument, String direction) {
            return Optional.empty();
        }
        @Override public List<MarketThesis> findByMarket(UUID marketId) { return List.of(); }
    }
}
