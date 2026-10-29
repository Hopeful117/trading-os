package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.ctrader.authentication.CTraderEnvironment;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenLifecycleService;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenMaterial;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenRecord;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenStore;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.ACCOUNT_AUTH_RESPONSE;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.ACCOUNT_LIST_RESPONSE;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.APPLICATION_AUTH_RESPONSE;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.UNSUBSCRIBE_LIVE_TRENDBAR_RESPONSE;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.UNSUBSCRIBE_SPOTS_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void authenticatesCorrelatesResponsesAndDispatchesSpotEvents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CTraderTokenLifecycleService tokenLifecycle = mock(CTraderTokenLifecycleService.class);
        when(tokenLifecycle.requireUsableToken(true)).thenReturn(token());
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        WebSocket socket = mock(WebSocket.class);
        List<Integer> requestTypes = new ArrayList<>();
        List<tools.jackson.databind.JsonNode> requests = new ArrayList<>();
        AtomicBoolean failUnsubscribe = new AtomicBoolean();
        when(socket.isOutputClosed()).thenReturn(false);
        when(socket.sendClose(anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(socket.sendText(anyString(), eq(true))).thenAnswer(invocation -> {
            var request = mapper.readTree(invocation.getArgument(0, String.class));
            requestTypes.add(request.path("payloadType").asInt());
            requests.add(request);
            int responseType = switch (request.path("payloadType").asInt()) {
                case 2100 -> APPLICATION_AUTH_RESPONSE;
                case 2149 -> ACCOUNT_LIST_RESPONSE;
                case 2102 -> ACCOUNT_AUTH_RESPONSE;
                case 2129 -> UNSUBSCRIBE_SPOTS_RESPONSE;
                case 2136 -> UNSUBSCRIBE_LIVE_TRENDBAR_RESPONSE;
                default -> throw new IllegalStateException("unexpected request");
            };
            if (request.path("payloadType").asInt() == 2129 && failUnsubscribe.get()) {
                listener.get().onText(socket, "{\"payloadType\":2130,\"clientMsgId\":\""
                        + request.path("clientMsgId").asText()
                        + "\",\"errorCode\":\"ERR_UNSUBSCRIBE\",\"description\":\"rejected\"}", true);
                return CompletableFuture.completedFuture(null);
            }
            String payload = responseType == ACCOUNT_LIST_RESPONSE
                    ? "{\"ctidTraderAccount\":[{\"ctidTraderAccountId\":42,\"isLive\":false}]}"
                    : "{}";
            listener.get().onText(socket, "{\"payloadType\":" + responseType
                    + ",\"clientMsgId\":\"" + request.path("clientMsgId").asText()
                    + "\",\"payload\":" + payload + "}", true);
            return CompletableFuture.completedFuture(null);
        });
        JsonWebSocketCTraderOpenApiClient client = new JsonWebSocketCTraderOpenApiClient(
                properties(Duration.ofSeconds(1)), tokenLifecycle, mapper,
                (endpoint, timeout, webSocketListener) -> {
                    listener.set(webSocketListener);
                    webSocketListener.onOpen(socket);
                    return CompletableFuture.completedFuture(socket);
                });
        CountDownLatch eventReceived = new CountDownLatch(1);
        List<CTraderOpenApiClient.CTraderStreamEvent> events = new ArrayList<>();

        CTraderOpenApiClient.CTraderStream stream = client.openStream(event -> {
            events.add(event);
            eventReceived.countDown();
        }, () -> { });
        listener.get().onText(socket, "{\"payloadType\":2131,\"payload\":{\"symbolId\":42,\"timestamp\":1000,\"bid\":123,\"ask\":124}}", true);
        stream.unsubscribeSpots(42);
        stream.unsubscribeLiveTrendbar(42, "M1");

        assertThat(eventReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(events).singleElement().isInstanceOf(CTraderOpenApiClient.CTraderSpotEvent.class);
        assertThat(requestTypes).contains(2129, 2136);
        assertThat(requests.stream().filter(request -> request.path("payloadType").asInt() == 2129)
                .findFirst().orElseThrow().path("payload").path("ctidTraderAccountId").asLong()).isEqualTo(42L);
        assertThat(requests.stream().filter(request -> request.path("payloadType").asInt() == 2129)
                .findFirst().orElseThrow().path("payload").path("symbolId").toString()).isEqualTo("[42]");
        assertThat(requests.stream().filter(request -> request.path("payloadType").asInt() == 2136)
                .findFirst().orElseThrow().path("payload").path("period").asInt()).isEqualTo(1);
        failUnsubscribe.set(true);
        assertThatThrownBy(() -> stream.unsubscribeSpots(42))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cTrader provider rejected the request: ERR_UNSUBSCRIBE (rejected)");
        stream.close();
        verify(socket).sendClose(anyInt(), eq("done"));
    }

    @Test
    void closesSessionWhenAuthenticationResponseTimesOut() {
        CTraderTokenLifecycleService tokenLifecycle = mock(CTraderTokenLifecycleService.class);
        when(tokenLifecycle.requireUsableToken(true)).thenReturn(token());
        WebSocket socket = mock(WebSocket.class);
        when(socket.isOutputClosed()).thenReturn(false);
        when(socket.sendClose(anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        JsonWebSocketCTraderOpenApiClient client = new JsonWebSocketCTraderOpenApiClient(
                properties(Duration.ofMillis(20)), tokenLifecycle, new ObjectMapper(),
                (endpoint, timeout, listener) -> {
                    listener.onOpen(socket);
                    return CompletableFuture.completedFuture(socket);
                });

        assertThatThrownBy(() -> client.openStream(event -> { }, () -> { }))
                .isInstanceOf(com.hope.trading.market_data.exception.MarketDataProviderException.class)
                .hasMessage("cTrader Open API response timed out");
        verify(socket).sendClose(anyInt(), eq("done"));
    }

    @Test
    void listenerQueuesResponsesAndSignalsTransportFailures() throws Exception {
        ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(2);
        List<String> events = new ArrayList<>();
        var terminated = new CountDownLatch(1);
        WebSocket.Listener listener = new JsonWebSocketCTraderOpenApiClient.Listener(messages,
                node -> events.add(node.path("payloadType").asText()), terminated::countDown,
                "diagnostic", "session", "fixture", 443);
        WebSocket socket = mock(WebSocket.class);

        listener.onText(socket, "{\"payloadType\":2101,\"clientMsgId\":\"1\",\"payload\":{}}", true);
        listener.onText(socket, "{\"payloadType\":2131,\"payload\":{}}", true);
        listener.onError(socket, new IllegalStateException("transport"));

        assertThat(messages).hasSize(1);
        assertThat(events).containsExactly("2131");
        assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private CTraderTokenStore.StoredCTraderToken token() {
        var record = new CTraderTokenRecord(UUID.randomUUID(), CTraderEnvironment.DEMO, 42L, 1,
                Instant.now().plusSeconds(60), Instant.now(), CTraderTokenStatus.ACTIVE);
        return new CTraderTokenStore.StoredCTraderToken(record,
                new CTraderTokenMaterial("access".toCharArray(), "refresh".toCharArray(), record.expiresAt()));
    }

    private CTraderMarketDataProperties properties(Duration timeout) {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "client", "secret",
                "wss://fixture", timeout, Map.of());
    }
}
