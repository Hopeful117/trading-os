package com.hope.trading.market_intelligence.domain.thesis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MarketThesisLevelSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    @Test
    void legacyLevelPayloadRemainsReadable() throws Exception {
        MarketThesisLevel level = mapper.readValue(
                "{\"reference\":\"swing-1\",\"role\":\"STRUCTURAL\","
                        + "\"timeframe\":\"H1\",\"value\":100}",
                MarketThesisLevel.class);

        assertThat(level.reference()).isEqualTo("swing-1");
        assertThat(level.value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(level.source()).isEqualTo("UNKNOWN");
        assertThat(level.sourceReference()).isEqualTo("swing-1");
    }

    @Test
    void enrichedLevelPayloadRoundTripsProvenance() throws Exception {
        MarketThesisLevel original = new MarketThesisLevel(
                "swing-1", "OBJECTIVE", "H1", new BigDecimal("110"),
                "TREND_CONTEXT", "evidence-1", null, null, null);

        MarketThesisLevel restored = mapper.readValue(
                mapper.writeValueAsString(original), MarketThesisLevel.class);

        assertThat(restored).isEqualTo(original);
    }
}
