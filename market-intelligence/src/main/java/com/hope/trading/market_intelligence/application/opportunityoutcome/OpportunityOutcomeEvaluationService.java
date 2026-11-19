package com.hope.trading.market_intelligence.application.opportunityoutcome;

import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationRequest;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.OpportunityOutcomeRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcome;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

@Service
public class OpportunityOutcomeEvaluationService {
    private final TradingOpportunityRepository opportunities;
    private final MarketOutcomeEvaluationService marketOutcomes;
    private final OpportunityOutcomeRepository outcomes;
    private final OpportunityEvidenceAdmissibility evidenceAdmissibility;
    private final Clock clock;

    public OpportunityOutcomeEvaluationService(
            TradingOpportunityRepository opportunities,
            MarketOutcomeEvaluationService marketOutcomes,
            OpportunityOutcomeRepository outcomes,
            OpportunityEvidenceAdmissibility evidenceAdmissibility,
            Clock clock
    ) {
        this.opportunities = Objects.requireNonNull(opportunities);
        this.marketOutcomes = Objects.requireNonNull(marketOutcomes);
        this.outcomes = Objects.requireNonNull(outcomes);
        this.evidenceAdmissibility = Objects.requireNonNull(evidenceAdmissibility);
        this.clock = Objects.requireNonNull(clock);
    }

    public OpportunityOutcome evaluate(OpportunityOutcomeEvaluationRequest request) {
        TradingOpportunity opportunity = opportunities.find(request.opportunityId(), request.opportunityVersion())
                .orElseThrow(() -> new IllegalArgumentException("Opportunity version not found"));
        String sourceFingerprint = sourceFingerprint(opportunity);

        if (request.evaluationCutoff().isBefore(opportunity.evaluatedAt())
                || request.evaluationCutoff().isBefore(opportunity.validFrom())) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.NON_EVALUABLE,
                    "OPPORTUNITY_EVIDENCE_AFTER_CUTOFF", null);
        }

        if (opportunity.status() == OpportunityStatus.INVALIDATED) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.INVALIDATED, "OPPORTUNITY_INVALIDATED", null);
        }
        if (opportunity.status() == OpportunityStatus.EXPIRED) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.EXPIRED, "OPPORTUNITY_EXPIRED", null);
        }

        Optional<TradingOpportunity> latest = opportunities.findLatest(opportunity.id());
        if (latest.isPresent()
                && latest.get().version().value() > opportunity.version().value()
                && !latest.get().evaluatedAt().isAfter(request.evaluationCutoff())) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.SUPERSEDED, "NEWER_OPPORTUNITY_VERSION", null);
        }

        OpportunityEvidenceAdmissibility.Decision evidence =
                evidenceAdmissibility.validate(opportunity, request.evaluationCutoff());
        if (!evidence.admissible()) {
            return persist(request, opportunity, sourceFingerprint,
                    evidence.status(), evidence.reason(), null);
        }

        if (opportunity.validUntil().map(value -> !value.isAfter(request.evaluationCutoff())).orElse(false)) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.EXPIRED, "OPPORTUNITY_VALIDITY_EXPIRED", null);
        }

        Instant now = clock.instant();
        if (now.isBefore(request.horizon().maturityAt(request.evaluationCutoff()))) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.NON_EVALUABLE, "HORIZON_NOT_MATURED", null);
        }

        if (opportunity.marketId().isEmpty()) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.NON_EVALUABLE, "MARKET_IDENTITY_UNAVAILABLE", null);
        }
        ObservedMarketOutcome marketOutcome = marketOutcomes.evaluate(new MarketOutcomeEvaluationRequest(
                opportunity.marketId().orElseThrow(),
                request.evaluationCutoff(), request.horizon(), request.analysisExecutionId(),
                request.historicalEvidenceSelection()));
        if (marketOutcome.status() == com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus.AVAILABLE) {
            return persist(request, opportunity, sourceFingerprint,
                    OpportunityOutcomeStatus.MARKET_MOVEMENT_OBSERVED, null,
                    ObservedMarketOutcomeIdentity.of(marketOutcome));
        }
        OpportunityOutcomeStatus status = switch (marketOutcome.status()) {
            case PENDING -> OpportunityOutcomeStatus.NON_EVALUABLE;
            case UNAVAILABLE -> OpportunityOutcomeStatus.UNKNOWN;
            case INCOMPLETE -> OpportunityOutcomeStatus.NON_EVALUABLE;
            case AVAILABLE -> throw new IllegalStateException("AVAILABLE outcome handled above");
        };
        return persist(request, opportunity, sourceFingerprint, status,
                "MARKET_OUTCOME_" + marketOutcome.status().name(), null);
    }

    private OpportunityOutcome persist(
            OpportunityOutcomeEvaluationRequest request,
            TradingOpportunity opportunity,
            String sourceFingerprint,
            OpportunityOutcomeStatus status,
            String reason,
            ObservedMarketOutcomeIdentity marketOutcome
    ) {
        return outcomes.saveIfAbsent(OpportunityOutcome.create(
                opportunity.id(), opportunity.version(), opportunity.marketId().orElse(null),
                sourceFingerprint, request.evaluationCutoff(), request.horizon(), status, reason,
                marketOutcome, clock.instant(), request.analysisExecutionId()));
    }

    private String sourceFingerprint(TradingOpportunity opportunity) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("opportunityId", opportunity.id().value());
        values.put("version", opportunity.version().value());
        values.put("marketId", opportunity.marketId().orElse(null));
        values.put("instrument", opportunity.instrument());
        values.put("direction", opportunity.direction());
        values.put("scenario", opportunity.scenario());
        values.put("timeframe", opportunity.timeframe());
        values.put("type", opportunity.type());
        values.put("origin", opportunity.origin());
        values.put("score", opportunity.score().value());
        values.put("observations", opportunity.observations());
        values.put("marketThesis", opportunity.marketThesis().orElse(null));
        values.put("setup", opportunity.setup().orElse(null));
        values.put("evaluatedAt", opportunity.evaluatedAt());
        values.put("validFrom", opportunity.validFrom());
        values.put("validUntil", opportunity.validUntil().orElse(null));
        return ArtifactFingerprint.ofParameters(values).value();
    }
}
