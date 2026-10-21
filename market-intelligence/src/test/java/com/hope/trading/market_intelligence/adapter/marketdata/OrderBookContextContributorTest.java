package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBookContextContributorTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEQUENCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CUTOFF = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void mapsStrictReplayToAvailableContextWithProvenance() {
        OrderBookReplayClient client = mock(OrderBookReplayClient.class);
        String identity = OrderBookReplayIdentity.forRequest(MARKET_ID, 25, CUTOFF);
        when(client.replay(MARKET_ID, 25, CUTOFF, identity)).thenReturn(response("AVAILABLE", "evidence"));

        var section = new OrderBookContextContributor(client).contribute(request());

        assertThat(section.type()).isEqualTo(ContextSectionType.ORDER_BOOK);
        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.provenance().evidenceFingerprint()).isEqualTo("evidence");
        assertThat(section.provenance().sourceReference()).isEqualTo(SEQUENCE_ID.toString());
        verify(client).replay(MARKET_ID, 25, CUTOFF, identity);
    }

    @Test
    void doesNotUseCurrentStateForNonStrictAnalysis() {
        OrderBookReplayClient client = mock(OrderBookReplayClient.class);
        var request = new IntelligenceAnalysisRequest(UUID.randomUUID(), MARKET_ID,
                AnalysisExecutionMode.PASSIVE, "liquidity", null, null, null, CUTOFF,
                EvidenceAcquisitionPolicy.LIVE_CURRENT);

        var section = new OrderBookContextContributor(client).contribute(request);

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNSUPPORTED);
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    private IntelligenceAnalysisRequest request() {
        return new IntelligenceAnalysisRequest(UUID.randomUUID(), MARKET_ID,
                AnalysisExecutionMode.PASSIVE, "liquidity", null, null, null, CUTOFF,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED);
    }

    private OrderBookReplayClient.ReplayResponse response(String status, String fingerprint) {
        return new OrderBookReplayClient.ReplayResponse(status, MARKET_ID, 25, SEQUENCE_ID,
                CUTOFF, CUTOFF.minusSeconds(1), CUTOFF, 0L, 1L, "KRAKEN", "PF_XBTUSD",
                "order-book-normalization-v1", fingerprint, "TRUSTED", List.of(fingerprint),
                List.of(new OrderBookReplayClient.LevelResponse(new BigDecimal("100"), new BigDecimal("1"))),
                List.of(new OrderBookReplayClient.LevelResponse(new BigDecimal("101"), new BigDecimal("1"))), null);
    }
}
