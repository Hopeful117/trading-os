package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcEventWireContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void serializesAndDeserializesAdditiveProvenanceMetadata() throws Exception {
        UUID marketId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant occurredAt = Instant.parse("2026-09-23T11:00:00Z");
        Instant fetchedAt = Instant.parse("2026-09-23T11:00:02Z");
        OhlcEvent event = new OhlcEvent(
                marketId, MarketProvider.KRAKEN, "BTC/EUR", OhlcInterval.ONE_HOUR,
                Instant.parse("2026-09-23T10:00:00Z"),
                Instant.parse("2026-09-23T11:00:00Z"),
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 4,
                true, occurredAt, true, "normalizer:gap", fetchedAt);

        JsonNode payload = json.readTree(json.writeValueAsString(event));
        assertThat(payload.path("marketId").asText()).isEqualTo(marketId.toString());
        assertThat(payload.path("symbol").asText()).isEqualTo("BTC/EUR");
        assertThat(payload.path("synthetic").asBoolean()).isTrue();
        assertThat(payload.path("sourceId").asText()).isEqualTo("normalizer:gap");
        assertThat(payload.path("fetchedAt").asText()).isEqualTo(fetchedAt.toString());

        OhlcEvent decoded = json.readValue(payload.toString(), OhlcEvent.class);
        assertThat(decoded.synthetic()).isTrue();
        assertThat(decoded.sourceId()).isEqualTo("normalizer:gap");
        assertThat(decoded.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(decoded.close()).isEqualByComparingTo("102");
    }
}
