package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.port.MarketThesisReference;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single deterministic rule set deciding whether a passive opportunity version is a
 * current passive candidate. Shared by the passive awareness feed (Story 0242) and
 * passive-to-active scan creation (Story 0243) so both decisions cannot diverge.
 */
@Component
public final class PassiveCandidateEvaluator {
    public static final String MARKET_IDENTITY_UNAVAILABLE = "MARKET_IDENTITY_UNAVAILABLE";
    public static final String THESIS_SUPERSEDED = "THESIS_SUPERSEDED";
    public static final String OPPORTUNITY_SUPERSEDED = "OPPORTUNITY_SUPERSEDED";

    private final TradingOpportunityRepository opportunities;
    private final ObservationRepository observations;
    private final MarketThesisRepository theses;
    private final StrategyMatchRepository strategyMatches;
    private final MarketDataClient marketData;

    public PassiveCandidateEvaluator(
            TradingOpportunityRepository opportunities,
            ObservationRepository observations,
            MarketThesisRepository theses,
            StrategyMatchRepository strategyMatches,
            MarketDataClient marketData) {
        this.opportunities = Objects.requireNonNull(opportunities);
        this.observations = Objects.requireNonNull(observations);
        this.theses = Objects.requireNonNull(theses);
        this.strategyMatches = Objects.requireNonNull(strategyMatches);
        this.marketData = Objects.requireNonNull(marketData);
    }

    /** Evaluates already-latest opportunity versions with batched reference loading. */
    public List<PassiveAwarenessProjection> evaluate(List<TradingOpportunity> source, Instant now) {
        return evaluateBatch(source, now).projections();
    }

    /**
     * Evaluates one exact opportunity version, including whether a newer version exists.
     * Returns empty when the exact version does not exist.
     */
    public Optional<SingleEvaluation> evaluateOne(OpportunityId id, OpportunityVersion version, Instant now) {
        Optional<TradingOpportunity> exact = opportunities.find(id, version);
        if (exact.isEmpty()) {
            return Optional.empty();
        }
        TradingOpportunity opportunity = exact.orElseThrow();
        Batch batch = evaluateBatch(List.of(opportunity), now);
        PassiveAwarenessProjection projection = batch.projections().getFirst();
        boolean superseded = opportunities.findLatest(id)
                .map(latest -> latest.version().value() != version.value())
                .orElse(false);
        if (superseded) {
            List<String> diagnostics = new ArrayList<>(projection.diagnostics());
            diagnostics.add(OPPORTUNITY_SUPERSEDED);
            projection = new PassiveAwarenessProjection(projection.opportunity(), projection.market(),
                    projection.thesis(), projection.observations(), diagnostics, false);
        }
        return Optional.of(new SingleEvaluation(projection, batch.marketDataUnavailable()));
    }

    private Batch evaluateBatch(List<TradingOpportunity> source, Instant now) {
        MarketLookup markets = resolveMarkets(source);
        Map<UUID, StrategyMatch> matchesById = strategyMatches.findByIds(source.stream()
                        .map(value -> value.strategyMatchId().orElse(null)).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(StrategyMatch::matchId, Function.identity(), (left, right) -> left));
        Map<UUID, Observation> observationsById = observations.findByIds(source.stream()
                        .flatMap(value -> value.observations().stream())
                        .map(reference -> reference.observationId()).toList())
                .stream()
                .collect(Collectors.toMap(Observation::id, Function.identity(), (left, right) -> left));
        Set<MarketThesisReference> thesisReferences = source.stream()
                .flatMap(value -> value.marketThesis().stream())
                .map(value -> new MarketThesisReference(value.thesisId(), value.thesisVersion()))
                .collect(Collectors.toSet());
        Map<MarketThesisReference, MarketThesis> thesesByReference = theses.findByIdAndVersions(thesisReferences)
                .stream()
                .collect(Collectors.toMap(value -> new MarketThesisReference(value.id(), value.version()),
                        Function.identity(), (left, right) -> left));
        Set<UUID> supersededTheses = thesisReferences.isEmpty() ? Set.of()
                : Objects.requireNonNullElse(theses.findSupersededThesisIds(thesisReferences.stream()
                        .map(MarketThesisReference::thesisId).collect(Collectors.toSet())), Set.of());
        List<PassiveAwarenessProjection> projections = source.stream()
                .map(value -> project(value, now, markets.byId(), matchesById, observationsById,
                        thesesByReference, supersededTheses))
                .toList();
        return new Batch(projections, markets.unavailable());
    }

    private PassiveAwarenessProjection project(TradingOpportunity opportunity, Instant now,
            Map<UUID, MarketResponse> marketsById,
            Map<UUID, StrategyMatch> matchesById,
            Map<UUID, Observation> observationsById,
            Map<MarketThesisReference, MarketThesis> thesesByReference,
            Set<UUID> supersededTheses) {
        List<String> diagnostics = new ArrayList<>();
        if (opportunity.status() != OpportunityStatus.ACTIVE) {
            diagnostics.add("OPPORTUNITY_NOT_ACTIVE");
        }
        if (!contains(opportunity.validFrom(), opportunity.validUntil().orElse(null), now)) {
            diagnostics.add("OPPORTUNITY_OUTSIDE_VALIDITY");
        }
        if (opportunity.strategyMatchId().isEmpty()) {
            diagnostics.add("STRATEGY_MATCH_MISSING");
        } else {
            StrategyMatch match = matchesById.get(opportunity.strategyMatchId().orElseThrow());
            if (match == null) {
                diagnostics.add("STRATEGY_MATCH_REFERENCE_UNAVAILABLE");
            } else {
                if (!Objects.equals(match.marketId(), opportunity.marketId().orElse(null))) {
                    diagnostics.add("STRATEGY_MATCH_MARKET_MISMATCH");
                }
                if (!match.direction().name().equals(opportunity.direction().name())) {
                    diagnostics.add("STRATEGY_MATCH_DIRECTION_MISMATCH");
                }
                if (opportunity.observations().stream()
                        .noneMatch(reference -> reference.observationId().equals(match.observationId()))) {
                    diagnostics.add("STRATEGY_MATCH_OBSERVATION_MISMATCH");
                }
                if (opportunity.analysisExecutionId().isPresent()
                        && !opportunity.analysisExecutionId().orElseThrow().equals(match.analysisExecutionId())) {
                    diagnostics.add("STRATEGY_MATCH_EXECUTION_MISMATCH");
                }
                if (match.matchedAt().isAfter(now)) {
                    diagnostics.add("STRATEGY_MATCH_FRESHNESS_INVALID");
                }
            }
        }

        List<Observation> resolvedObservations = opportunity.observations().stream()
                .map(reference -> observationsById.get(reference.observationId()))
                .filter(Objects::nonNull).toList();
        if (resolvedObservations.size() != opportunity.observations().size()) {
            diagnostics.add("OBSERVATION_REFERENCE_UNAVAILABLE");
        }
        if (resolvedObservations.stream()
                .anyMatch(value -> value.status() != ObservationStatus.ACTIVE
                        || !contains(value.validFrom(), value.validUntil().orElse(null), now))) {
            diagnostics.add("OBSERVATION_NOT_CURRENT");
        }

        MarketThesis thesis = opportunity.marketThesis()
                .map(reference -> thesesByReference.get(new MarketThesisReference(
                        reference.thesisId(), reference.thesisVersion()))).orElse(null);
        if (thesis == null) {
            diagnostics.add("MARKET_THESIS_REFERENCE_UNAVAILABLE");
        } else {
            if (thesis.state() != MarketThesisState.CANDIDATE) {
                diagnostics.add("THESIS_NOT_CANDIDATE");
            }
            if (!contains(thesis.validFrom(), thesis.validUntil().orElse(null), now)) {
                diagnostics.add("THESIS_OUTSIDE_VALIDITY");
            }
            if (supersededTheses.contains(thesis.id())) {
                diagnostics.add(THESIS_SUPERSEDED);
            }
            if (thesis.evaluatedAt().isAfter(now) || thesis.sourceCutoff().isAfter(now)) {
                diagnostics.add("THESIS_FRESHNESS_INVALID");
            }
            if (thesis.hasBlockingContradiction()) {
                diagnostics.add("THESIS_BLOCKING_CONTRADICTION");
            }
            if (thesis.evidence().stream().anyMatch(this::requiredEvidenceNonPositive)) {
                diagnostics.add("REQUIRED_EVIDENCE_NON_POSITIVE");
            }
            if (thesis.evidence().stream().anyMatch(evidence -> evidence.required()
                    && evidence.validUntil() != null && !evidence.validUntil().isAfter(now))) {
                diagnostics.add("REQUIRED_EVIDENCE_EXPIRED");
            }
            if (thesis.evidence().stream().anyMatch(evidence -> evidence.required()
                    && (evidence.observedAt().isAfter(now)
                    || evidence.sourceCutoff() != null && evidence.sourceCutoff().isAfter(now)))) {
                diagnostics.add("REQUIRED_EVIDENCE_FRESHNESS_INVALID");
            }
            if (!thesis.marketId().equals(opportunity.marketId().orElse(null))
                    || !thesis.instrument().equalsIgnoreCase(opportunity.instrument())) {
                diagnostics.add("MARKET_THESIS_OPPORTUNITY_MISMATCH");
            }
        }

        MarketResponse market = resolveMarket(opportunity, diagnostics, marketsById);
        return new PassiveAwarenessProjection(opportunity, market, thesis, resolvedObservations, diagnostics,
                diagnostics.isEmpty());
    }

    private MarketLookup resolveMarkets(List<TradingOpportunity> source) {
        List<UUID> ids = source.stream().map(value -> value.marketId().orElse(null))
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new MarketLookup(Map.of(), false);
        }
        try {
            return new MarketLookup(marketData.findMarketsByIds(ids).stream()
                    .collect(Collectors.toMap(MarketResponse::marketId, Function.identity(),
                            (left, right) -> left)), false);
        } catch (RuntimeException unavailable) {
            return new MarketLookup(Map.of(), true);
        }
    }

    private MarketResponse resolveMarket(TradingOpportunity opportunity, List<String> diagnostics,
            Map<UUID, MarketResponse> marketsById) {
        if (opportunity.marketId().isEmpty()) {
            diagnostics.add("MARKET_IDENTITY_MISSING");
            return null;
        }
        if (opportunity.sourceScanId().isEmpty() || opportunity.sourceScanMarketId().isEmpty()
                || opportunity.analysisExecutionId().isEmpty()) {
            diagnostics.add("PASSIVE_PROVENANCE_INCOMPLETE");
        } else if (!opportunity.sourceScanMarketId().orElseThrow().equals(opportunity.marketId().orElseThrow())) {
            diagnostics.add("PASSIVE_SCAN_MARKET_MISMATCH");
        }
        MarketResponse market = marketsById.get(opportunity.marketId().orElseThrow());
        if (market == null) {
            diagnostics.add(MARKET_IDENTITY_UNAVAILABLE);
            return null;
        }
        if (!market.marketId().equals(opportunity.marketId().orElseThrow())
                || !market.symbol().equalsIgnoreCase(opportunity.instrument())) {
            diagnostics.add("MARKET_IDENTITY_MISMATCH");
        }
        return market;
    }

    private boolean requiredEvidenceNonPositive(MarketThesisEvidence evidence) {
        return evidence.required() && (evidence.role() != MarketThesisEvidenceRole.SUPPORTING
                || evidence.availability() != MarketThesisAvailability.AVAILABLE);
    }

    private static boolean contains(Instant validFrom, Instant validUntil, Instant now) {
        return !validFrom.isAfter(now) && (validUntil == null || validUntil.isAfter(now));
    }

    /** Result of evaluating one exact candidate version. */
    public record SingleEvaluation(PassiveAwarenessProjection projection, boolean marketDataUnavailable) {
    }

    private record Batch(List<PassiveAwarenessProjection> projections, boolean marketDataUnavailable) {
    }

    private record MarketLookup(Map<UUID, MarketResponse> byId, boolean unavailable) {
    }
}
