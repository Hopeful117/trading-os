package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcRangeAnalysisCapabilityTest {
    @Test
    void calculatesDeterministicHistoricalRange() {
        UUID marketId = UUID.randomUUID();
        HistoricalOhlcContext history = new HistoricalOhlcContext(
                marketId,
                "FIFTEEN_MINUTES",
                List.of(
                        candle("100", "110", "90", "105"),
                        candle("105", "120", "100", "115")
                )
        );
        IntelligenceContext context = new IntelligenceContext(Map.of(
                ContextSectionType.HISTORICAL_OHLC,
                new ContextSection(
                        ContextSectionType.HISTORICAL_OHLC,
                        ContextSectionStatus.AVAILABLE,
                        ContextSensitivity.PUBLIC,
                        history,
                        new ContextProvenance("market-data", Instant.now(), Instant.now()),
                        null
                )
        ));

        IntelligenceFinding finding = new OhlcRangeAnalysisCapability()
                .analyze(
                        new IntelligenceAnalysisRequest(
                                UUID.randomUUID(), marketId,
                                AnalysisExecutionMode.ACTIVE, null
                        ),
                        context
                )
                .findings()
                .getFirst();

        assertThat(finding.metrics().get("highestPrice")).isEqualByComparingTo("120");
        assertThat(finding.metrics().get("lowestPrice")).isEqualByComparingTo("90");
        assertThat(finding.metrics().get("range")).isEqualByComparingTo("30");
    }

    private OhlcPoint candle(String open, String high, String low, String close) {
        Instant now = Instant.now();
        return new OhlcPoint(
                now, now.plusSeconds(900),
                new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), BigDecimal.ONE
        );
    }
}
