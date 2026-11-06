package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.adapter.marketdata.StrictObservedOhlcEvidenceValidator;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class HistoricalReconstructionPreflight {
    private static final String INTERVAL = "FIFTEEN_MINUTES";
    private static final int MAX_LIMIT = 720;
    private final HistoricalOhlcClient client;
    private final TrendContextProfile profile;

    public HistoricalReconstructionPreflight(HistoricalOhlcClient client, TrendContextProfile profile) {
        this.client = client;
        this.profile = profile;
    }

    public Result verify(UUID marketId, Instant cutoff) {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(cutoff, "cutoff is required");
        List<String> identities = new ArrayList<>();
        for (var definition : profile.roles().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.role().name())).toList()) {
            int limit = Math.min(definition.requestedCandles(), MAX_LIMIT);
            var response = client.findHistoricalOhlc(marketId, definition.interval(), cutoff, limit);
            StrictObservedOhlcEvidenceValidator.validate(response, marketId, definition.interval(), cutoff);
            long eligible = response.observations().stream()
                    .filter(value -> value.closeTime() != null && !value.closeTime().isAfter(cutoff))
                    .count();
            if (definition.required() && eligible < definition.minimumEligibleCandles()) {
                throw new IllegalStateException("Historical role window is insufficient: " + definition.role());
            }
            response.observations().forEach(value -> identities.add(
                    definition.role() + "|" + value.observationId() + "|" + value.observationFingerprint()));
        }
        Instant maturity = cutoff.plus(Duration.ofHours(1));
        var future = client.findHistoricalOhlcRange(marketId, INTERVAL, cutoff, maturity, "CAPTURED");
        if (future == null || !Objects.equals(future.marketId(), marketId)
                || !Objects.equals(future.interval(), INTERVAL)
                || !Objects.equals(future.fromExclusive(), cutoff)
                || !Objects.equals(future.toInclusive(), maturity)
                || !Objects.equals(future.evidencePolicy(), "CAPTURED")
                || future.expectedObservationCount() != 4
                || future.observedObservationCount() != 4 || !future.gaps().isEmpty()) {
            throw new IllegalStateException("Historical PT1H future range is incomplete");
        }
        future.observations().forEach(value -> {
            if (value.observationId() == null || value.observationFingerprint() == null
                    || value.fetchedAt() == null || value.capturedAt() == null || value.knownAt() == null
                    || !value.closed() || value.synthetic() || !"CAPTURED".equals(value.origin())
                    || !"historical-ohlc-v1".equals(value.normalizationVersion())) {
                throw new IllegalStateException("Historical future evidence provenance is incomplete");
            }
            identities.add("FUTURE|" + value.observationId() + "|" + value.observationFingerprint());
        });
        return new Result(List.copyOf(identities), orderedFingerprint(identities));
    }

    private String orderedFingerprint(List<String> identities) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", identities).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Result(List<String> selectedEvidence, String selectionFingerprint) {
        public Result {
            selectedEvidence = List.copyOf(selectedEvidence);
        }
    }
}
