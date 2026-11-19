package com.hope.trading.market_intelligence.application.opportunityoutcome;

import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class OpportunityEvidenceAdmissibility {
    private final ObservationRepository observations;
    private final MarketThesisRepository theses;
    private final StrategyMatchRepository strategyMatches;

    public OpportunityEvidenceAdmissibility(ObservationRepository observations,
                                            MarketThesisRepository theses,
                                            StrategyMatchRepository strategyMatches) {
        this.observations = Objects.requireNonNull(observations);
        this.theses = Objects.requireNonNull(theses);
        this.strategyMatches = Objects.requireNonNull(strategyMatches);
    }

    public Decision validate(TradingOpportunity opportunity, Instant cutoff) {
        if (opportunity.setup().isPresent()
                && opportunity.setup().orElseThrow().detectedAt().isAfter(cutoff)) {
            return unknown("EVIDENCE_AFTER_CUTOFF");
        }
        for (var reference : opportunity.observations()) {
            Observation observation = observations.findById(reference.observationId()).orElse(null);
            if (observation == null) return unknown("OBSERVATION_REFERENCE_UNAVAILABLE");
            if (observation.createdAt().isAfter(cutoff)
                    || observation.validFrom().isAfter(cutoff)
                    || observation.observedAt().isAfter(cutoff)) {
                return unknown("OBSERVATION_EVIDENCE_AFTER_CUTOFF");
            }
        }
        if (opportunity.marketThesis().isPresent()) {
            var reference = opportunity.marketThesis().orElseThrow();
            MarketThesis thesis = theses.findByIdAndVersion(reference.thesisId(), reference.thesisVersion()).orElse(null);
            if (thesis == null) return unknown("MARKET_THESIS_REFERENCE_UNAVAILABLE");
            if (thesis.evaluatedAt().isAfter(cutoff) || thesis.sourceCutoff().isAfter(cutoff)
                    || thesis.validFrom().isAfter(cutoff)
                    || thesis.evidence().stream().anyMatch(e -> e.observedAt().isAfter(cutoff)
                    || e.sourceCutoff() != null && e.sourceCutoff().isAfter(cutoff))) {
                return unknown("THESIS_EVIDENCE_AFTER_CUTOFF");
            }
        }
        if (opportunity.strategyMatchId().isPresent()) {
            var match = strategyMatches.findById(opportunity.strategyMatchId().orElseThrow()).orElse(null);
            if (match == null) return unknown("STRATEGY_MATCH_REFERENCE_UNAVAILABLE");
            if (match.matchedAt().isAfter(cutoff)) return unknown("STRATEGY_MATCH_AFTER_CUTOFF");
        }
        return new Decision(true, null, null);
    }

    private static Decision unknown(String reason) {
        return new Decision(false, com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus.UNKNOWN, reason);
    }

    public record Decision(boolean admissible,
                           com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus status,
                           String reason) { }
}
