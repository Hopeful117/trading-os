package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.thesis.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MarketThesisJpaPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Autowired
    private JpaMarketThesisRepository repository;

    @Test
    void immutableThesisRoundTripsThroughJpa() {
        UUID marketId = UUID.randomUUID();
        MarketThesisEvidence evidence = new MarketThesisEvidence(
                UUID.randomUUID(), MarketThesisEvidenceRole.MISSING,
                MarketThesisAvailability.NOT_IMPLEMENTED, null,
                "MARKET_INTELLIGENCE", "MOMENTUM", null,
                "Momentum is not implemented", NOW, null);
        MarketThesis thesis = new MarketThesis(
                UUID.randomUUID(), UUID.randomUUID(), 1, marketId, "BTC/EUR",
                MarketThesisState.NO_TRADE, null, List.of(evidence),
                List.of("MOMENTUM_UNAVAILABLE"), NOW, NOW, NOW, null,
                UUID.randomUUID(), "market-thesis-composer/v1", "b".repeat(64), null);

        repository.save(thesis);
        MarketThesis reloaded = repository.findByIdAndVersion(thesis.id(), 1).orElseThrow();
        MarketThesis existing = repository.saveIfAbsent(thesis);

        assertThat(reloaded.id()).isEqualTo(thesis.id());
        assertThat(reloaded.lineageId()).isEqualTo(thesis.lineageId());
        assertThat(reloaded.state()).isEqualTo(MarketThesisState.NO_TRADE);
        assertThat(reloaded.evidence()).containsExactly(evidence);
        assertThat(reloaded.reasons()).containsExactly("MOMENTUM_UNAVAILABLE");
        assertThat(existing.id()).isEqualTo(thesis.id());
        assertThat(repository.findLatest(marketId, "BTC/EUR", "NONE"))
                .hasValueSatisfying(value -> assertThat(value.id()).isEqualTo(thesis.id()));
    }
}
