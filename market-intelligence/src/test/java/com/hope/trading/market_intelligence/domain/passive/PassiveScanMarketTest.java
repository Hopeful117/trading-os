package com.hope.trading.market_intelligence.domain.passive;

import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassiveScanMarketTest {
    @Test
    void preservesIdentityAndRejectsBackwardTransitions() {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        PassiveScanMarket market = PassiveScanMarket.registered(UUID.randomUUID(), UUID.randomUUID(), 0,
                UUID.randomUUID(), "CTRADER", "CFD", Duration.ofMinutes(5),
                TradeabilityEvidenceProfile.FOREX_QUOTE_BASIC, UUID.randomUUID(), now);

        PassiveScanMarket claimed = market.advanceTo(PassiveScanMarketStatus.DISPATCH_REQUESTED, now.plusSeconds(1))
                .advanceTo(PassiveScanMarketStatus.ACCEPTED, now.plusSeconds(2));

        assertThat(claimed.marketId()).isEqualTo(market.marketId());
        assertThat(claimed.status()).isEqualTo(PassiveScanMarketStatus.ACCEPTED);
        assertThatThrownBy(() -> claimed.advanceTo(PassiveScanMarketStatus.REGISTERED, now.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }
}
