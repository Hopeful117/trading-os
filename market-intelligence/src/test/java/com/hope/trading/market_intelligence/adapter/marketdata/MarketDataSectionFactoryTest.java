package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataSectionFactoryTest {
    @Test
    void identifiesStaleContext() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        MarketSnapshotContext payload = new MarketSnapshotContext(
                UUID.randomUUID(), "BTC/USD", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, true,
                Instant.now().minusSeconds(60)
        );

        ContextSection section = factory.available(
                ContextSectionType.MARKET_SNAPSHOT,
                payload,
                payload.occurredAt()
        );

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(section.provenance().source()).isEqualTo("market-data");
    }
}
