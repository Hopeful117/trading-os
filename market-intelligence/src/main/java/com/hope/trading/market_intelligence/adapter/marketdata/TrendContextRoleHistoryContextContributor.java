package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Acquires all Trend Context roles inside one cutoff boundary. */
@Component
public class TrendContextRoleHistoryContextContributor implements ContextContributor {
    private static final Logger log = LoggerFactory.getLogger(TrendContextRoleHistoryContextContributor.class);
    public static final String RULE_VERSION = "trend-context-rules-v1";
    private static final int MAX_OHLC_LIMIT = 720;
    private static final int MAX_ATTEMPTS = 5;

    private final OhlcEvidenceSource liveSource;
    private final OhlcEvidenceSource historicalSource;
    private final TrendContextInputMapper inputMapper;
    private final TrendContextProfile profile;
    private final Clock clock;

    @Autowired
    public TrendContextRoleHistoryContextContributor(
            LiveOhlcEvidenceSource liveSource,
            HistoricalOhlcEvidenceSource historicalSource,
            TrendContextInputMapper inputMapper,
            TrendContextProfile profile,
            Clock clock
    ) {
        this.liveSource = liveSource;
        this.historicalSource = historicalSource;
        this.inputMapper = inputMapper;
        this.profile = profile;
        this.clock = clock;
    }

    public TrendContextRoleHistoryContextContributor(
            MarketDataClient marketDataClient,
            TrendContextInputMapper inputMapper,
            TrendContextProfile profile,
            Clock clock
    ) {
        this(new LiveOhlcEvidenceSource(marketDataClient),
                new HistoricalOhlcContextContributor.UnavailableHistoricalOhlcEvidenceSource(),
                inputMapper, profile, clock);
    }

    TrendContextRoleHistoryContextContributor(
            OhlcEvidenceSource liveSource,
            OhlcEvidenceSource historicalSource,
            TrendContextInputMapper inputMapper,
            TrendContextProfile profile,
            Clock clock
    ) {
        this.liveSource = liveSource;
        this.historicalSource = historicalSource;
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
        Instant boundary = request.evaluationCutoff() == null
                ? clock.instant() : request.evaluationCutoff();
        EnumMap<TrendContextRole, Integer> limits = new EnumMap<>(TrendContextRole.class);
        profile.roles().forEach((role, definition) ->
                limits.put(role, Math.min(definition.requestedCandles(), MAX_OHLC_LIMIT)));

        Map<TrendContextRole, List<OhlcResponse>> responses = Map.of();
        AcquisitionMetadata metadata = AcquisitionMetadata.empty();
        TrendContextAssessmentInput mapped = null;
        boolean evidenceExcludedByCutoff = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            AcquisitionResult acquired = acquire(request, limits);
            Map<TrendContextRole, List<OhlcResponse>> acquiredResponses = acquired.responses();
            metadata = acquired.metadata();
            evidenceExcludedByCutoff |= acquiredResponses.values().stream()
                    .flatMap(Collection::stream)
                    .anyMatch(value -> after(value.openTime(), boundary)
                            || after(value.closeTime(), boundary)
                            || after(value.occurredAt(), boundary)
                            || after(value.fetchedAt(), boundary));
            responses = filterToCutoff(acquiredResponses, boundary);
            boolean retry = false;
            try {
                mapped = inputMapper.map(
                        responses, profile, boundary, boundary, RULE_VERSION);
            } catch (IllegalArgumentException exception) {
                log.warn("Trend Context input mapping failed analysis={} market={} attempt={} errorType={} message={}",
                        request.analysisId(), request.marketId(), attempt + 1,
                        exception.getClass().getSimpleName(), exception.getMessage());
                retry = increaseMissingRequiredRole(responses, limits);
                if (!retry) {
                    return ContextSection.incomplete(
                            ContextRequirement.optionalPublic(sectionType()),
                            "Trend Context history is incomplete at the evaluation cutoff");
                }
            }
            if (!retry && !increaseForInsufficientHistory(mapped, limits)) {
                break;
            }
        }

        if (responses.values().stream().allMatch(List::isEmpty)) {
            return evidenceExcludedByCutoff
                    ? ContextSection.incomplete(
                    ContextRequirement.optionalPublic(sectionType()),
                    "Trend Context history is incomplete at the evaluation cutoff")
                    : ContextSection.missing(
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
                new ContextProvenance("market-data", occurredAt, boundary,
                        null, null, metadata.normalizationVersion(), request.evidencePolicy(),
                        metadata.evidenceFingerprint(), metadata.observationCount()),
                occurredAt == null ? "Trend Context history is unavailable" : null
        );
    }

    private Map<TrendContextRole, List<OhlcResponse>> filterToCutoff(
            Map<TrendContextRole, List<OhlcResponse>> responses,
            Instant cutoff
    ) {
        EnumMap<TrendContextRole, List<OhlcResponse>> filtered =
                new EnumMap<>(TrendContextRole.class);
        responses.forEach((role, values) -> filtered.put(role, values.stream()
                .filter(value -> !after(value.openTime(), cutoff)
                        && !after(value.closeTime(), cutoff)
                        && !after(value.occurredAt(), cutoff)
                        && !after(value.fetchedAt(), cutoff))
                .toList()));
        return Map.copyOf(filtered);
    }

    private boolean after(Instant value, Instant cutoff) {
        return value != null && value.isAfter(cutoff);
    }

    private AcquisitionResult acquire(
            IntelligenceAnalysisRequest request, Map<TrendContextRole, Integer> limits) {
        EnumMap<TrendContextRole, List<OhlcResponse>> responses =
                new EnumMap<>(TrendContextRole.class);
        List<String> fingerprints = new ArrayList<>();
        List<String> normalizations = new ArrayList<>();
        int observationCount = 0;
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleDefinition definition = profile.roles().get(role);
            if (definition == null) continue;
            List<OhlcResponse> values;
            try {
                OhlcEvidenceSource.OhlcEvidenceBatch batch = source(request).acquire(
                        request.marketId(), definition.interval(), limits.get(role),
                        request.evaluationCutoff());
                values = batch.observations();
                if (batch.evidenceFingerprint() != null) {
                    fingerprints.add(batch.evidenceFingerprint());
                }
                if (batch.normalizationVersion() != null) {
                    normalizations.add(batch.normalizationVersion());
                }
                observationCount += batch.observationCount();
            } catch (RuntimeException exception) {
                log.warn("Trend Context OHLC acquisition failed analysis={} market={} role={} interval={} limit={} errorType={} message={}",
                        request.analysisId(), request.marketId(), role, definition.interval(),
                        limits.get(role), exception.getClass().getSimpleName(), exception.getMessage());
                throw new IllegalStateException(
                        "Trend Context OHLC acquisition failed for " + role, exception);
            }
            if (!values.isEmpty() || definition.required()) responses.put(role, List.copyOf(values));
        }
        String normalizationVersion = normalizations.stream().distinct().sorted()
                .reduce((left, right) -> left + "," + right).orElse(null);
        AcquisitionMetadata metadata = new AcquisitionMetadata(
                fingerprints.isEmpty() ? null
                        : ArtifactFingerprint.ofInputs(fingerprints.stream().sorted().toList()).value(),
                normalizationVersion, observationCount);
        return new AcquisitionResult(Map.copyOf(responses), metadata);
    }

    private record AcquisitionResult(
            Map<TrendContextRole, List<OhlcResponse>> responses,
            AcquisitionMetadata metadata
    ) { }

    private record AcquisitionMetadata(
            String evidenceFingerprint,
            String normalizationVersion,
            int observationCount
    ) {
        private static AcquisitionMetadata empty() {
            return new AcquisitionMetadata(null, null, 0);
        }
    }

    private OhlcEvidenceSource source(IntelligenceAnalysisRequest request) {
        return request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                ? historicalSource : liveSource;
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
        int next = Math.clamp(Math.multiplyExact(current, 2), current + 1, MAX_OHLC_LIMIT);
        limits.put(role, next);
        return true;
    }
}
