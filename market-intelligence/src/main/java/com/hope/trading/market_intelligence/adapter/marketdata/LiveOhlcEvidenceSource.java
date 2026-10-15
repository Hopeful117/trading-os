package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class LiveOhlcEvidenceSource implements OhlcEvidenceSource {
    private final MarketDataClient client;

    public LiveOhlcEvidenceSource(MarketDataClient client) {
        this.client = client;
    }

    @Override
    public OhlcEvidenceBatch acquire(UUID marketId, String interval, int limit, Instant cutoff) {
        List<OhlcResponse> observations = client.findOhlc(marketId, interval, limit);
        String fingerprint = ArtifactFingerprint.ofInputs(observations.stream()
                .map(String::valueOf).toList()).value();
        return new OhlcEvidenceBatch(observations, fingerprint, null, observations.size());
    }
}
