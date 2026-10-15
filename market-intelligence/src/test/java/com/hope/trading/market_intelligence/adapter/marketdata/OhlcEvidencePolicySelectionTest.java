package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OhlcEvidencePolicySelectionTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void livePolicyUsesLiveSourceAndStrictPolicyUsesHistoricalSource() {
        OhlcEvidenceSource live = mock(OhlcEvidenceSource.class);
        OhlcEvidenceSource historical = mock(OhlcEvidenceSource.class);
        OhlcResponse candle = candle();
        when(live.acquire(MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF))
                .thenReturn(new OhlcEvidenceSource.OhlcEvidenceBatch(List.of(candle), "live", null, 1));
        when(historical.acquire(MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF))
                .thenReturn(new OhlcEvidenceSource.OhlcEvidenceBatch(List.of(candle), "historical", "v1", 1));
        HistoricalOhlcContextContributor contributor = new HistoricalOhlcContextContributor(
                live, historical, new MarketDataSectionFactory(Duration.ofSeconds(30)));

        ContextSection liveSection = contributor.contribute(request(EvidenceAcquisitionPolicy.LIVE_CURRENT));
        ContextSection strictSection = contributor.contribute(request(EvidenceAcquisitionPolicy.STRICT_OBSERVED));

        assertThat(liveSection.provenance().evidencePolicy())
                .isEqualTo(EvidenceAcquisitionPolicy.LIVE_CURRENT);
        assertThat(strictSection.provenance().evidencePolicy())
                .isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(strictSection.provenance().evidenceFingerprint()).isEqualTo("historical");
        verify(live).acquire(MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF);
        verify(historical).acquire(MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF);
    }

    private IntelligenceAnalysisRequest request(EvidenceAcquisitionPolicy policy) {
        return new IntelligenceAnalysisRequest(UUID.randomUUID(), MARKET_ID,
                AnalysisExecutionMode.ACTIVE, "replay", null, null, null, CUTOFF, policy);
    }

    private OhlcResponse candle() {
        Instant open = CUTOFF.minusSeconds(1800);
        return new OhlcResponse(MARKET_ID, "KRAKEN", "BTC/EUR", "FIFTEEN_MINUTES", open,
                open.plusSeconds(900), new BigDecimal("100"), new BigDecimal("105"),
                new BigDecimal("95"), new BigDecimal("102"), BigDecimal.TEN,
                new BigDecimal("101"), 2, true, open, false, "source", CUTOFF.minusSeconds(60));
    }
}
