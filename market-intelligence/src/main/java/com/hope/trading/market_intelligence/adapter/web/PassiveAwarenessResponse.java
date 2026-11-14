package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PassiveAwarenessResponse(
        UUID opportunityId,
        long opportunityVersion,
        boolean current,
        List<String> diagnostics,
        String lifecycleState,
        String presentationState,
        String instrument,
        String direction,
        String scenario,
        String timeframe,
        String origin,
        UUID marketId,
        String provider,
        String productType,
        UUID strategyMatchId,
        List<UUID> observationIds,
        List<Instant> observationTimes,
        UUID thesisId,
        Long thesisVersion,
        UUID sourceScanId,
        UUID sourceScanMarketId,
        UUID analysisExecutionId,
        Instant evaluatedAt,
        Instant sourceCutoff,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt
) {
    public PassiveAwarenessResponse {
        diagnostics = List.copyOf(diagnostics);
        observationIds = List.copyOf(observationIds);
    }

    public static PassiveAwarenessResponse from(PassiveAwarenessProjection projection) {
        var opportunity = projection.opportunity();
        var market = projection.market();
        var thesis = projection.thesis();
        var thesisReference = opportunity.marketThesis().orElse(null);
        return new PassiveAwarenessResponse(
                opportunity.id().value(), opportunity.version().value(), projection.current(),
                projection.diagnostics(), opportunity.status().name(),
                projection.current() ? "PASSIVE_CANDIDATE" : "NON_POSITIVE", opportunity.instrument(),
                opportunity.direction().name(), opportunity.scenario(), opportunity.timeframe(),
                opportunity.origin().name(), opportunity.marketId().orElse(null),
                market == null ? null : market.provider(),
                market == null ? null : market.productType(),
                opportunity.strategyMatchId().orElse(null),
                opportunity.observations().stream().map(reference -> reference.observationId()).toList(),
                projection.observations().stream().map(observation -> observation.observedAt()).toList(),
                thesis == null ? (thesisReference == null ? null : thesisReference.thesisId())
                        : thesis.id(),
                thesis == null ? (thesisReference == null ? null
                        : Long.valueOf(thesisReference.thesisVersion()))
                        : Long.valueOf(thesis.version()), opportunity.sourceScanId().orElse(null),
                opportunity.sourceScanMarketId().orElse(null), opportunity.analysisExecutionId().orElse(null),
                opportunity.evaluatedAt(), thesis == null ? null : thesis.sourceCutoff(),
                opportunity.validFrom(), opportunity.validUntil().orElse(null), opportunity.createdAt());
    }
}
