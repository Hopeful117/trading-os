package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWebSocketCTraderOpenApiClientTest {
    @Test
    void buildsSpotSubscriptionWithRepeatedSymbolAndTimestamp() {
        var payload = JsonWebSocketCTraderOpenApiClient.spotSubscriptionPayload(
                new ObjectMapper(), 49289512L, 42L);

        assertThat(payload.path("ctidTraderAccountId").asLong()).isEqualTo(49289512L);
        assertThat(payload.path("symbolId").isArray()).isTrue();
        assertThat(payload.path("symbolId").size()).isEqualTo(1);
        assertThat(payload.path("symbolId").get(0).asLong()).isEqualTo(42L);
        assertThat(payload.path("subscribeToSpotTimestamp").asBoolean()).isTrue();
    }

    @Test
    void buildsOneSpotSubscriptionForMultipleSymbols() {
        var payload = JsonWebSocketCTraderOpenApiClient.spotSubscriptionPayload(
                new ObjectMapper(), 49289512L, List.of(42L, 43L));

        assertThat(payload.path("symbolId").size()).isEqualTo(2);
        assertThat(payload.path("symbolId").get(0).asLong()).isEqualTo(42L);
        assertThat(payload.path("symbolId").get(1).asLong()).isEqualTo(43L);
    }
}
