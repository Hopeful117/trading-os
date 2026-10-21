package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookReplayIdentity;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookRetentionClient;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ResearchArtifactPopulationService {
    private static final Logger log = LoggerFactory.getLogger(ResearchArtifactPopulationService.class);
    private final ResearchLifecycleProperties properties;
    private final OrderBookRetentionClient retention;
    private final Set<String> populatedIdentities = ConcurrentHashMap.newKeySet();
    private final AtomicInteger nextMarketIndex = new AtomicInteger();

    public ResearchArtifactPopulationService(ResearchLifecycleProperties properties,
                                             OrderBookRetentionClient retention) {
        this.properties = properties;
        this.retention = retention;
    }

    public PopulationResult populate(Instant cutoff) {
        if (!properties.isArtifactPopulationEnabled()) {
            return PopulationResult.disabled();
        }
        int attempted = 0;
        int promoted = 0;
        int skipped = 0;
        int failed = 0;
        var markets = properties.getMarketIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        if (markets.isEmpty()) {
            return new PopulationResult(0, 0, 0, 0);
        }
        int start = Math.floorMod(nextMarketIndex.get(), markets.size());
        for (int offset = 0; offset < markets.size(); offset++) {
            int marketIndex = (start + offset) % markets.size();
            UUID marketId = markets.get(marketIndex);
            String identity = OrderBookReplayIdentity.forRequest(
                    marketId, properties.getArtifactPopulationDepth(), cutoff);
            if (populatedIdentities.contains(identity)) {
                skipped++;
                continue;
            }
            if (attempted >= properties.getArtifactPopulationMaxPerRun()) {
                break;
            }
            attempted++;
            nextMarketIndex.set((marketIndex + 1) % markets.size());
            try {
                if (retention.isReusable(identity)) {
                    populatedIdentities.add(identity);
                    skipped++;
                    continue;
                }
                retention.promoteCanonical(new OrderBookRetentionClient.CanonicalPromotionRequest(
                        marketId, properties.getArtifactPopulationDepth(), cutoff, identity,
                        properties.getArtifactPopulationPromotionReason()));
                populatedIdentities.add(identity);
                promoted++;
            } catch (RuntimeException exception) {
                failed++;
                log.warn("Replay artifact population failed market={} cutoff={} errorType={}",
                        marketId, cutoff, exception.getClass().getSimpleName());
            }
        }
        PopulationResult result = new PopulationResult(attempted, promoted, skipped, failed);
        log.info("Replay artifact population completed cutoff={} attempted={} promoted={} skipped={} failed={}",
                cutoff, attempted, promoted, skipped, failed);
        return result;
    }

    public record PopulationResult(int attempted, int promoted, int skipped, int failed) {
        static PopulationResult disabled() {
            return new PopulationResult(0, 0, 0, 0);
        }
    }
}
