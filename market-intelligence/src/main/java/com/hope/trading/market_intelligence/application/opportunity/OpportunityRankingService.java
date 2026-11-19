package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OpportunityRankingService {
    private final TradingOpportunityRepository opportunities;
    private final MarketThesisRepository theses;
    private final OpportunityRankingEngine engine;
    private final OpportunityRankingPolicy policy;

    public OpportunityRankingService(TradingOpportunityRepository opportunities,
                                     MarketThesisRepository theses,
                                     OpportunityRankingEngine engine,
                                     OpportunityRankingPolicy policy) {
        this.opportunities = Objects.requireNonNull(opportunities, "opportunities");
        this.theses = Objects.requireNonNull(theses, "theses");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public OpportunityRankingResult rankLatest() {
        return rank(opportunities.findAllLatest());
    }

    public OpportunityRankingResult rank(List<TradingOpportunity> values) {
        Objects.requireNonNull(values, "values");
        List<OpportunityRankingCandidate> candidates = new ArrayList<>();
        List<OpportunityRankingResult.ExcludedOpportunity> excluded = new ArrayList<>();
        for (TradingOpportunity opportunity : values) {
            if (!eligibleStatus(opportunity.status())) {
                excluded.add(excluded(opportunity, "TERMINAL_OPPORTUNITY",
                        "Opportunity is not in a rankable lifecycle state"));
                continue;
            }
            if (opportunity.marketThesis().isEmpty()) {
                excluded.add(excluded(opportunity, "MISSING_EXACT_EVIDENCE_REFERENCE",
                        "Opportunity does not reference an exact Market Thesis"));
                continue;
            }
            var reference = opportunity.marketThesis().orElseThrow();
            MarketThesis thesis = theses.findByIdAndVersion(reference.thesisId(), reference.thesisVersion())
                    .orElse(null);
            if (thesis == null) {
                excluded.add(excluded(opportunity, "MISSING_EXACT_EVIDENCE_REFERENCE",
                        "Referenced Market Thesis version was not found"));
                continue;
            }
            if (!thesis.id().equals(reference.thesisId())
                    || thesis.version() != reference.thesisVersion()) {
                excluded.add(excluded(opportunity, "MISMATCHED_EXACT_EVIDENCE_REFERENCE",
                        "Repository returned a Market Thesis with a different identity or version"));
                continue;
            }
            if (thesis.evidenceQuality().isEmpty()) {
                excluded.add(excluded(opportunity, "UNASSESSED_EVIDENCE",
                        "Referenced Market Thesis has no persisted evidence-quality assessment"));
                continue;
            }
            candidates.add(new OpportunityRankingCandidate(opportunity, reference,
                    thesis.evidenceQuality().orElseThrow(), thesis.inputFingerprint()));
        }
        excluded.sort(Comparator
                .comparing(OpportunityRankingResult.ExcludedOpportunity::opportunityId)
                .thenComparingLong(OpportunityRankingResult.ExcludedOpportunity::opportunityVersion)
                .thenComparing(OpportunityRankingResult.ExcludedOpportunity::code));
        return new OpportunityRankingResult(policy.version(), engine.rankCandidates(candidates, policy), excluded);
    }

    private static boolean eligibleStatus(OpportunityStatus status) {
        return status == OpportunityStatus.DETECTED
                || status == OpportunityStatus.ANALYZED
                || status == OpportunityStatus.ACTIVE;
    }

    private static OpportunityRankingResult.ExcludedOpportunity excluded(
            TradingOpportunity opportunity, String code, String explanation) {
        return new OpportunityRankingResult.ExcludedOpportunity(
                opportunity.id().value(), opportunity.version().value(), code, explanation);
    }
}
