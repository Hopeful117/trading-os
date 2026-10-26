package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CTraderWireFixtureTest {
    @Test
    void decodesOfficialRelativeTrendbarFieldsFromWireFixture() throws IOException {
        var root = new ObjectMapper().readTree(getClass().getResourceAsStream("/ctrader/live-trendbar.json"));
        var bar = JsonWebSocketCTraderOpenApiClient.decodeTrendbar(
                root.path("payload").path("trendbar").get(0));

        assertThat(bar.timestamp()).isEqualTo(1792929540L);
        assertThat(bar.open()).isEqualTo(1L);
        assertThat(bar.high()).isEqualTo(3L);
        assertThat(bar.low()).isEqualTo(12345L);
        assertThat(bar.close()).isEqualTo(2L);
    }
}
