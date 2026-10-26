package com.hope.trading.market_data.ctrader;

import com.hope.trading.ctrader.openapi.protocol.CTraderRequestCorrelation;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenLifecycleService;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.*;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class JsonWebSocketCTraderOpenApiClient implements CTraderOpenApiClient {
    private final CTraderMarketDataProperties properties;
    private final CTraderTokenLifecycleService tokenLifecycle;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JsonWebSocketCTraderOpenApiClient(CTraderMarketDataProperties properties,
                                             CTraderTokenLifecycleService tokenLifecycle,
                                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenLifecycle = tokenLifecycle;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CTraderSymbol> symbols() {
        return withAuthenticatedSession(session -> {
            JsonNode response = session.request(SYMBOL_LIST_REQUEST, SYMBOL_LIST_RESPONSE, accountPayload());
            List<CTraderSymbol> result = new ArrayList<>();
            for (JsonNode node : response.path("symbol")) result.add(mapSymbol(node));
            return List.copyOf(result);
        });
    }

    @Override
    public CTraderSymbol symbol(long symbolId) {
        return withAuthenticatedSession(session -> mapSymbol(session.request(SYMBOL_BY_ID_REQUEST,
                SYMBOL_BY_ID_RESPONSE, accountPayload().put("symbolId", symbolId)).path("symbol")));
    }

    @Override
    public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) {
        if (from == null || to == null || !from.isBefore(to) || count < 1) {
            throw new IllegalArgumentException("bounded trendbar range and positive count are required");
        }
        return CTraderTrendbarPagination.collect(from, to, count, properties.maxTrendbarPages(),
                properties.maxTrendbarRows(), request -> trendbarPage(symbolId, period, request.from(), request.to(), request.count())).stream().toList();
    }

    @Override
    public CTraderTrendbarPage trendbarPage(long symbolId, String period, Instant from, Instant to, int count) {
        return withAuthenticatedSession(session -> {
            ObjectNode payload = accountPayload().put("symbolId", symbolId).put("period", periodValue(period))
                    .put("fromTimestamp", from.toEpochMilli()).put("toTimestamp", to.toEpochMilli())
                    .put("count", count);
            JsonNode response = session.request(GET_TRENDBARS_REQUEST, GET_TRENDBARS_RESPONSE, payload);
            List<CTraderTrendbar> result = new ArrayList<>();
            for (JsonNode node : response.path("trendbar")) {
                result.add(decodeTrendbar(node));
            }
            return new CTraderTrendbarPage(result, response.path("hasMore").asBoolean(false));
        });
    }

    @Override
    public CTraderStream openStream(Consumer<CTraderStreamEvent> events, Runnable termination) {
        try {
            required(properties.clientId(), "clientId");
            required(properties.clientSecret(), "clientSecret");
            CTraderTokenStore.StoredCTraderToken token = tokenLifecycle.requireUsableToken(true);
            Session session = new Session(node -> dispatch(node, events), termination);
            long accountId = token.record().externalAccountId();
            try (token) {
                session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                        objectMapper.createObjectNode().put("clientId", properties.clientId())
                                .put("clientSecret", properties.clientSecret()));
                char[] access = token.material().copyAccessToken();
                try {
                JsonNode accounts = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                        objectMapper.createObjectNode().put("accessToken", new String(access)));
                validateEnvironment(accounts, accountId);
                JsonNode accountAuth = session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                        objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                                .put("accessToken", new String(access)));
                } finally {
                    Arrays.fill(access, '\0');
                }
            }
            return new Stream(session, accountId);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cTrader Market Data stream unavailable", exception);
        }
    }

    private void dispatch(JsonNode response, Consumer<CTraderStreamEvent> events) {
        JsonNode payload = response.path("payload");
        switch (response.path("payloadType").asInt()) {
            case SPOT_EVENT -> {
                long symbolId = payload.path("symbolId").asLong(0);
                long timestamp = payload.path("timestamp").asLong(0);
                if (symbolId > 0 && timestamp > 0) {
                    events.accept(new CTraderSpotEvent(symbolId,
                            payload.has("bid") ? payload.path("bid").asLong() : null,
                            payload.has("ask") ? payload.path("ask").asLong() : null,
                            Instant.ofEpochMilli(timestamp)));
                }
                for (JsonNode bar : payload.path("trendbar")) {
                    long barTimestamp = trendbarTimestamp(bar);
                    String period = periodName(bar.path("period"));
                    if (symbolId > 0 && barTimestamp > 0 && !period.isBlank()
                            && bar.has("low") && bar.has("deltaOpen") && bar.has("deltaHigh") && bar.has("deltaClose")) {
                        events.accept(new CTraderLiveTrendbarEvent(symbolId, period,
                                decodeTrendbar(bar),
                                Instant.ofEpochSecond(barTimestamp)));
                    }
                }
            }
            default -> { }
        }
    }

    static CTraderTrendbar decodeTrendbar(JsonNode node) {
        if (!node.isObject() || trendbarTimestamp(node) <= 0 || !node.has("low")
                || !node.has("deltaOpen") || !node.has("deltaHigh") || !node.has("deltaClose")) {
            throw new IllegalStateException("cTrader trendbar wire payload is incomplete");
        }
        return new CTraderTrendbar(trendbarTimestamp(node), node.path("deltaOpen").asLong(),
                node.path("deltaHigh").asLong(), node.path("low").asLong(),
                node.path("deltaClose").asLong(), node.path("volume").asLong());
    }

    private static long trendbarTimestamp(JsonNode node) {
        if (node.has("utcTimestamp")) return node.path("utcTimestamp").asLong(0);
        return node.path("utcTimestampInMinutes").asLong(0) * 60;
    }

    private ObjectNode accountPayload() {
        try (CTraderTokenStore.StoredCTraderToken token = tokenLifecycle.requireUsableToken(true)) {
            return objectMapper.createObjectNode().put("ctidTraderAccountId", token.record().externalAccountId());
        }
    }

    private <T> T withAuthenticatedSession(SessionWork<T> work) {
        try (CTraderTokenStore.StoredCTraderToken token = tokenLifecycle.requireUsableToken(true);
             Session session = new Session()) {
            required(properties.clientId(), "clientId");
            required(properties.clientSecret(), "clientSecret");
            session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("clientId", properties.clientId())
                            .put("clientSecret", properties.clientSecret()));
            char[] access = token.material().copyAccessToken();
            try {
                ObjectNode account = objectMapper.createObjectNode().put("ctidTraderAccountId",
                        token.record().externalAccountId()).put("accessToken", new String(access));
                JsonNode accounts = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                        objectMapper.createObjectNode().put("accessToken", new String(access)));
                validateEnvironment(accounts, token.record().externalAccountId());
                session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE, account);
            } finally {
                Arrays.fill(access, '\0');
            }
            return work.apply(session);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cTrader Market Data operation unavailable", exception);
        }
    }

    private CTraderSymbol mapSymbol(JsonNode node) {
        if (!node.isObject() || node.path("symbolId").asLong() <= 0
                || node.path("symbolName").asText().isBlank()) {
            throw new IllegalStateException("cTrader symbol metadata is incomplete");
        }
        return new CTraderSymbol(node.path("symbolId").asLong(), node.path("symbolName").asText(),
                node.path("digits").asInt(), node.path("minVolume").asLong(),
                node.path("maxVolume").asLong(), node.path("stepVolume").asLong());
    }

    private int periodValue(String period) {
        return switch (period) {
            case "M1" -> 1; case "M5" -> 5; case "M15" -> 7; case "M30" -> 8;
            case "H1" -> 9; case "H4" -> 10; case "H12" -> 11; case "D1" -> 12;
            case "W1" -> 13;
            default -> throw new IllegalArgumentException("Unsupported cTrader trendbar period: " + period);
        };
    }

    private String periodName(JsonNode period) {
        if (period.isTextual() && !period.asText().isBlank()) return period.asText();
        return switch (period.asInt(0)) {
            case 1 -> "M1"; case 5 -> "M5"; case 7 -> "M15"; case 8 -> "M30";
            case 9 -> "H1"; case 10 -> "H4"; case 11 -> "H12"; case 12 -> "D1";
            case 13 -> "W1";
            default -> "";
        };
    }

    private void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("cTrader " + name + " is not configured");
    }

    private void validateEnvironment(JsonNode accountList, long accountId) {
        JsonNode account = null;
        for (JsonNode candidate : accountList.path("ctidTraderAccount")) {
            if (candidate.path("ctidTraderAccountId").asLong() == accountId) {
                account = candidate;
                break;
            }
        }
        if (account == null || !account.path("isLive").isBoolean()) {
            throw new IllegalStateException("cTrader configured account was not returned with environment");
        }
        boolean expectedLive = properties.environment() == com.hope.trading.market_data.ctrader.authentication.CTraderEnvironment.LIVE;
        if (account.path("isLive").asBoolean() != expectedLive) {
            throw new IllegalStateException("cTrader account environment does not match configuration");
        }
    }

    @FunctionalInterface
    private interface SessionWork<T> { T apply(Session session); }

    private final class Session implements AutoCloseable {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>(256);
        private final WebSocket socket;

        private Session() { this(node -> { }, () -> { }); }

        private Session(Consumer<JsonNode> events, Runnable termination) {
            try {
                required(properties.websocketEndpoint(), "websocketEndpoint");
                if (!"wss".equalsIgnoreCase(URI.create(properties.websocketEndpoint()).getScheme())) {
                    throw new IllegalStateException("cTrader websocket endpoint must use WSS");
                }
                socket = httpClient.newWebSocketBuilder().connectTimeout(properties.requestTimeout())
                        .buildAsync(URI.create(properties.websocketEndpoint()), new Listener(messages, events, termination))
                        .orTimeout(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
            } catch (Exception exception) {
                throw new IllegalStateException("cTrader Open API transport unavailable", exception);
            }
        }

        private JsonNode request(int requestType, int responseType, ObjectNode payload) {
            String clientMessageId = CTraderRequestCorrelation.create().clientMessageId();
            ObjectNode message = objectMapper.createObjectNode().put("clientMsgId", clientMessageId)
                    .put("payloadType", requestType).set("payload", payload);
            socket.sendText(message.toString(), true)
                    .orTimeout(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
            long deadline = System.nanoTime() + properties.requestTimeout().toNanos();
            try {
                while (System.nanoTime() < deadline) {
                    long remainingMillis = Math.max(1, Math.min(1000,
                            Duration.ofNanos(deadline - System.nanoTime()).toMillis()));
                    String raw = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
                    if (raw == null) continue;
                    JsonNode response = objectMapper.readTree(raw);
                    if (clientMessageId.equals(response.path("clientMsgId").asText())
                            && response.path("payloadType").asInt() == responseType) return response.path("payload");
                    if (clientMessageId.equals(response.path("clientMsgId").asText())
                            && (response.path("errorCode").isTextual()
                            || response.path("payload").path("errorCode").isTextual())) {
                        String errorCode = response.path("errorCode").asText(
                                response.path("payload").path("errorCode").asText("unknown"));
                        String description = response.path("description").asText(
                                response.path("payload").path("description").asText(""));
                        throw new IllegalStateException("cTrader provider rejected the request: "
                                + errorCode + (description.isBlank() ? "" : " (" + description + ")"));
                    }
                }
                throw new IllegalStateException("cTrader Open API response timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("cTrader Open API request interrupted", exception);
            }
        }

        @Override public void close() { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
    }

    private final class Stream implements CTraderStream {
        private final Session session;
        private final long accountId;

        private Stream(Session session, long accountId) { this.session = session; this.accountId = accountId; }

        @Override public void subscribeSpots(long symbolId) {
            session.request(SUBSCRIBE_SPOTS_REQUEST, SUBSCRIBE_SPOTS_RESPONSE,
                    accountPayload().put("symbolId", symbolId));
        }

        @Override public void unsubscribeSpots(long symbolId) {
            throw new UnsupportedOperationException("cTrader unsubscribe is unavailable; close the bounded session");
        }

        @Override public void subscribeLiveTrendbar(long symbolId, String period) {
            session.request(SUBSCRIBE_LIVE_TRENDBAR_REQUEST, SUBSCRIBE_LIVE_TRENDBAR_RESPONSE,
                    accountPayload().put("symbolId", symbolId).put("period", periodValue(period)));
        }

        @Override public void unsubscribeLiveTrendbar(long symbolId, String period) {
            throw new UnsupportedOperationException("cTrader unsubscribe is unavailable; close the bounded session");
        }
        @Override public void close() { session.close(); }

        private ObjectNode accountPayload() { return objectMapper.createObjectNode().put("ctidTraderAccountId", accountId); }
    }

    private static final class Listener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final Consumer<JsonNode> events;
        private final Runnable termination;
        private final StringBuilder text = new StringBuilder();
        private Listener(BlockingQueue<String> messages, Consumer<JsonNode> events, Runnable termination) {
            this.messages = messages; this.events = events; this.termination = termination;
        }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String value = text.toString();
                text.setLength(0);
                try {
                    JsonNode node = new ObjectMapper().readTree(value);
                    int payloadType = node.path("payloadType").asInt();
                     if (payloadType == SPOT_EVENT) events.accept(node);
                    else enqueueOrTerminate(value);
                } catch (RuntimeException ignored) { enqueueOrTerminate(value); }
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        private void enqueueOrTerminate(String value) {
            if (!messages.offer(value)) {
                termination.run();
            }
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            termination.run();
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket socket, Throwable error) { termination.run(); }
    }
}
