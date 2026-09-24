package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Acquires all Trend Context roles inside one cutoff boundary. */
@Component
public class TrendContextRoleHistoryContextContributor implements ContextContributor {
    public static final String RULE_VERSION = "trend-context-rules-v1";
    private static final int MAX_OHLC_LIMIT = 720;
    private static final int MAX_ATTEMPTS = 5;

    private final MarketDataClient marketDataClient;
    private final TrendContextInputMapper inputMapper;
    private final TrendContextProfile profile;
    private final Clock clock;

    public TrendContextRoleHistoryContextContributor(
            MarketDataClient marketDataClient,
            TrendContextInputMapper inputMapper,
            TrendContextProfile profile,
            Clock clock
    ) {
        this.marketDataClient = marketDataClient;
        this.inputMapper = inputMapper;
        this.profile = profile;
        this.clock = clock;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.TREND_CONTEXT;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        Instant boundary = clock.instant();
        EnumMap<TrendContextRole, Integer> limits = new EnumMap<>(TrendContextRole.class);
        profile.roles().forEach((role, definition) ->
                limits.put(role, Math.min(definition.requestedCandles(), MAX_OHLC_LIMIT)));

        Map<TrendContextRole, List<OhlcResponse>> responses = Map.of();
        TrendContextAssessmentInput mapped = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            responses = acquire(request.marketId(), limits);
            try {
                mapped = inputMapper.map(
                        responses, profile, boundary, boundary, RULE_VERSION);
            } catch (IllegalArgumentException exception) {
                if (!increaseMissingRequiredRole(responses, limits)) throw exception;
                continue;
            }
            if (!increaseForInsufficientHistory(mapped, responses, limits)) break;
        }

        if (responses.values().stream().allMatch(List::isEmpty)) {
            return ContextSection.missing(
                    ContextRequirement.optionalPublic(sectionType()),
                    "Trend Context history is unavailable");
        }

        TrendContextRoleHistory history = new TrendContextRoleHistory(
                responses, boundary, boundary, profile.profileId(),
                profile.profileVersion(), RULE_VERSION, limits);
        Instant occurredAt = responses.values().stream()
                .flatMap(Collection::stream)
                .map(OhlcResponse::occurredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ContextSection(
                sectionType(),
                occurredAt == null ? ContextSectionStatus.MISSING : ContextSectionStatus.AVAILABLE,
                ContextSensitivity.PUBLIC,
                history,
                new ContextProvenance("market-data", occurredAt, boundary),
                occurredAt == null ? "Trend Context history is unavailable" : null
        );
    }

    private Map<TrendContextRole, List<OhlcResponse>> acquire(
            UUID marketId, Map<TrendContextRole, Integer> limits) {
        EnumMap<TrendContextRole, List<OhlcResponse>> responses =
                new EnumMap<>(TrendContextRole.class);
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleDefinition definition = profile.roles().get(role);
            if (definition == null) continue;
            List<OhlcResponse> values = marketDataClient.findOhlc(
                    marketId, definition.interval(), limits.get(role));
            if (!values.isEmpty() || definition.required()) responses.put(role, List.copyOf(values));
        }
        return Map.copyOf(responses);
    }

    private boolean increaseMissingRequiredRole(
            Map<TrendContextRole, List<OhlcResponse>> responses,
            Map<TrendContextRole, Integer> limits) {
        boolean increased = false;
        for (Map.Entry<TrendContextRole, TrendContextRoleDefinition> entry : profile.roles().entrySet()) {
            if (!entry.getValue().required()
                    && responses.getOrDefault(entry.getKey(), List.of()).isEmpty()) continue;
            if (responses.getOrDefault(entry.getKey(), List.of()).isEmpty()) {
                increased |= increase(entry.getKey(), limits);
            }
        }
        return increased;
    }

    private boolean increaseForInsufficientHistory(
            TrendContextAssessmentInput input,
            Map<TrendContextRole, List<OhlcResponse>> responses,
            Map<TrendContextRole, Integer> limits) {
        boolean increased = false;
        for (Map.Entry<TrendContextRole, TrendContextRoleDefinition> entry : profile.roles().entrySet()) {
            TrendContextRoleSeries series = input.roleSeries().get(entry.getKey());
            if (series != null
                    && series.calculationReadyCandles().size() < entry.getValue().minimumEligibleCandles()) {
                increased |= increase(entry.getKey(), limits);
            }
        }
        return increased;
    }

    private boolean increase(TrendContextRole role, Map<TrendContextRole, Integer> limits) {
        int current = limits.get(role);
        if (current >= MAX_OHLC_LIMIT) return false;
        int next = Math.min(MAX_OHLC_LIMIT, Math.max(current + 1, current * 2));
        limits.put(role, next);
        return true;
    }
}
