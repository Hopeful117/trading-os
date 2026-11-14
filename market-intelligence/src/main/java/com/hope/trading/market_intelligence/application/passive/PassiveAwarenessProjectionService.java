package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.port.MarketThesisReference;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public final class PassiveAwarenessProjectionService {
    private static final int MAX_SOURCE_ITEMS = 1_000;
    private final TradingOpportunityRepository opportunities;
    private final ObservationRepository observations;
    private final MarketThesisRepository theses;
    private final StrategyMatchRepository strategyMatches;
    private final MarketDataClient marketData;
    private final Clock clock;

    public PassiveAwarenessProjectionService(
            TradingOpportunityRepository opportunities,
            ObservationRepository observations,
            MarketThesisRepository theses,
            StrategyMatchRepository strategyMatches,
            MarketDataClient marketData,
            Clock clock) {
        this.opportunities = Objects.requireNonNull(opportunities);
        this.observations = Objects.requireNonNull(observations);
        this.theses = Objects.requireNonNull(theses);
        this.strategyMatches = Objects.requireNonNull(strategyMatches);
        this.marketData = Objects.requireNonNull(marketData);
        this.clock = Objects.requireNonNull(clock);
    }

    public PassiveAwarenessPage search(PassiveAwarenessQuery query) {
        Instant now = clock.instant();
        List<TradingOpportunity> loadedSource = opportunities.findLatestPassive(MAX_SOURCE_ITEMS + 1);
        boolean truncated = loadedSource.size() > MAX_SOURCE_ITEMS;
        List<TradingOpportunity> source = loadedSource.stream().limit(MAX_SOURCE_ITEMS).toList();
        Map<java.util.UUID, MarketResponse> marketsById = resolveMarkets(source);
        Map<java.util.UUID, StrategyMatch> matchesById = strategyMatches.findByIds(source.stream()
                .map(value -> value.strategyMatchId().orElse(null)).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(StrategyMatch::matchId, Function.identity()));
        Map<java.util.UUID, Observation> observationsById = observations.findByIds(source.stream()
                .flatMap(value -> value.observations().stream()).map(reference -> reference.observationId()).toList()).stream()
                .collect(Collectors.toMap(Observation::id, Function.identity()));
        Set<MarketThesisReference> thesisReferences = source.stream().flatMap(value -> value.marketThesis().stream())
                .map(value -> new MarketThesisReference(value.thesisId(), value.thesisVersion())).collect(Collectors.toSet());
        Map<MarketThesisReference, MarketThesis> thesesByReference = theses.findByIdAndVersions(thesisReferences).stream()
                .collect(Collectors.toMap(value -> new MarketThesisReference(value.id(), value.version()), Function.identity()));
        List<PassiveAwarenessProjection> filtered = source.stream()
                .filter(value -> value.origin() == OpportunityOrigin.PASSIVE_SCAN)
                .map(value -> project(value, now, marketsById, matchesById, observationsById, thesesByReference))
                .filter(value -> matches(value, query))
                .filter(value -> !query.currentOnly() || value.current())
                .sorted(Comparator
                        .comparing((PassiveAwarenessProjection value) -> value.opportunity().evaluatedAt())
                        .reversed()
                        .thenComparing(value -> value.opportunity().instrument())
                        .thenComparing(value -> value.opportunity().id().value()))
                .toList();
        int from = Math.min(query.page() * query.size(), filtered.size());
        int to = Math.min(from + query.size(), filtered.size());
        return new PassiveAwarenessPage(filtered.subList(from, to), query.page(), query.size(),
                filtered.size(), truncated);
    }

    private PassiveAwarenessProjection project(TradingOpportunity opportunity, Instant now,
            Map<java.util.UUID, MarketResponse> marketsById,
            Map<java.util.UUID, StrategyMatch> matchesById,
            Map<java.util.UUID, Observation> observationsById,
            Map<MarketThesisReference, MarketThesis> thesesByReference) {
        List<String> diagnostics = new ArrayList<>();
        if (opportunity.status() != com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus.ACTIVE) {
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
        if (resolvedObservations.stream().filter(Objects::nonNull)
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

    private Map<java.util.UUID, MarketResponse> resolveMarkets(List<TradingOpportunity> source) {
        try {
            List<java.util.UUID> ids = source.stream().map(value -> value.marketId().orElse(null))
                    .filter(Objects::nonNull).distinct().toList();
            return marketData.findMarketsByIds(ids).stream()
                    .collect(Collectors.toMap(MarketResponse::marketId, Function.identity(), (left, right) -> left));
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private MarketResponse resolveMarket(TradingOpportunity opportunity, List<String> diagnostics,
            Map<java.util.UUID, MarketResponse> marketsById) {
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
            diagnostics.add("MARKET_IDENTITY_UNAVAILABLE");
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

    private boolean matches(PassiveAwarenessProjection value, PassiveAwarenessQuery query) {
        MarketResponse market = value.market();
        return (query.instrument() == null || value.opportunity().instrument()
                .equalsIgnoreCase(query.instrument()))
                && (query.provider() == null || market != null
                && query.provider().equalsIgnoreCase(market.provider()))
                && (query.productType() == null || market != null
                && query.productType().equalsIgnoreCase(market.productType()));
    }

    private boolean contains(Instant validFrom, Instant validUntil, Instant now) {
        return !validFrom.isAfter(now) && (validUntil == null || validUntil.isAfter(now));
    }
}
