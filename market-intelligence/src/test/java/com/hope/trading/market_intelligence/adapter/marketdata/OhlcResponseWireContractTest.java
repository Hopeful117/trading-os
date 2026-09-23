package com.hope.trading.market_intelligence.adapter.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OhlcResponseWireContractTest {
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void preservesAdditiveProvenanceWhenSerializedAndDeserialized() throws Exception {
        UUID marketId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant fetchedAt = Instant.parse("2026-09-23T11:00:02Z");
        OhlcResponse response = new OhlcResponse(
                marketId, "KRAKEN", "BTC/EUR", "1H",
                Instant.parse("2026-09-23T10:00:00Z"),
                Instant.parse("2026-09-23T11:00:00Z"),
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 4,
                true, Instant.parse("2026-09-23T11:00:00Z"), true,
                "normalizer:gap", fetchedAt);

        JsonNode payload = json.readTree(json.writeValueAsString(response));
        assertThat(payload.path("synthetic").asBoolean()).isTrue();
        assertThat(payload.path("sourceId").asText()).isEqualTo("normalizer:gap");
        assertThat(payload.path("fetchedAt").asText()).isEqualTo(fetchedAt.toString());

        OhlcResponse decoded = json.readValue(payload.toString(), OhlcResponse.class);
        assertThat(decoded.marketId()).isEqualTo(marketId);
        assertThat(decoded.symbol()).isEqualTo("BTC/EUR");
        assertThat(decoded.synthetic()).isTrue();
        assertThat(decoded.sourceId()).isEqualTo("normalizer:gap");
        assertThat(decoded.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(decoded.close()).isEqualByComparingTo("102");
    }
}
