package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactStatus;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactsResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketEligibilityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    @Test
    void rejectsEveryNonAvailableMarketFactsStatusWithoutExclusion() {
        MarketEligibilityPolicy policy = new MarketEligibilityPolicy(properties());

        for (MarketFactStatus status : new MarketFactStatus[]{
                MarketFactStatus.UNAVAILABLE,
                MarketFactStatus.UNSUPPORTED,
                MarketFactStatus.STALE,
                MarketFactStatus.INSUFFICIENT_DATA
        }) {
            MarketEligibilityPolicy.Evaluation result = policy.evaluate(facts(status, status, 24, 0));

            assertThat(result.status()).isEqualTo(MarketEligibilityStatus.NOT_EVALUABLE);
            assertThat(result.reasons()).isNotEmpty();
        }
    }

    @Test
    void rejectsIntegrityFailuresAndPreservesFactProvenance() {
        MarketEligibilityPolicy.Evaluation result = new MarketEligibilityPolicy(properties())
                .evaluate(facts(MarketFactStatus.AVAILABLE, MarketFactStatus.AVAILABLE, 24, 1));

        assertThat(result.status()).isEqualTo(MarketEligibilityStatus.NOT_EVALUABLE);
        assertThat(result.reasons()).contains(MarketEligibilityReason.DATA_INTEGRITY_FAILURE);
        assertThat(result.provenance().activityStatus()).isEqualTo("AVAILABLE");
        assertThat(result.provenance().readinessStatus()).isEqualTo("AVAILABLE");
        assertThat(result.provenance().generatedAt()).isEqualTo(NOW);
        assertThat(result.provenance().calculationVersion()).isEqualTo("market-facts-v1");
    }

    @Test
    void acceptsOnlyCompleteAvailableEvidence() {
        MarketEligibilityPolicy.Evaluation result = new MarketEligibilityPolicy(properties())
                .evaluate(facts(MarketFactStatus.AVAILABLE, MarketFactStatus.AVAILABLE, 24, 0));

        assertThat(result.status()).isEqualTo(MarketEligibilityStatus.ELIGIBLE);
        assertThat(result.reasons()).isEmpty();
    }

    private MarketEligibilityProperties properties() {
        MarketEligibilityProperties properties = new MarketEligibilityProperties();
        properties.setInterval(OhlcInterval.ONE_HOUR);
        properties.setActivityWindowMinutes(60);
        properties.setReadinessLookbackCandles(24);
        properties.setMinimumCompletedCandles(12);
        properties.setMaxObservationAgeSeconds(300);
        properties.setMaxMarketFactEvaluationsPerScan(10);
        return properties;
    }

    private MarketFactsResponse facts(
            MarketFactStatus activityStatus,
            MarketFactStatus readinessStatus,
            int observedCandles,
            int syntheticCandles
    ) {
        UUID marketId = UUID.randomUUID();
        return new MarketFactsResponse(
                marketId, "BTC/EUR", "BTC", "EUR", NOW,
                new MarketFactsResponse.MarketActivityResponse(
                        marketId, "KRAKEN", "BTC/EUR", "BTC", "EUR", OhlcInterval.ONE_HOUR,
                        Duration.ofHours(24), NOW.minus(Duration.ofHours(24)), NOW, NOW,
                        Duration.ofMinutes(5), BigDecimal.ONE, 24, 24, 24, 0, 0, 0,
                        activityStatus, null, "market-facts-v1"),
                new MarketFactsResponse.MarketReadinessResponse(
                        marketId, OhlcInterval.ONE_HOUR, 24, 24, 12, observedCandles,
                        24, syntheticCandles, 0, 0, 0, 0, NOW, NOW, NOW,
                        Duration.ofMinutes(5), readinessStatus, null, "market-facts-v1"));
    }
}
