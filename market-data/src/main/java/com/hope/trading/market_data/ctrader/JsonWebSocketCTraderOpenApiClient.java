package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.exception.MarketDataProviderException;
import com.hope.trading.ctrader.openapi.protocol.CTraderRequestCorrelation;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenLifecycleService;
import com.hope.trading.market_data.ctrader.authentication.CTraderTokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.*;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
@Slf4j
public class JsonWebSocketCTraderOpenApiClient implements CTraderOpenApiClient {
    private final CTraderMarketDataProperties properties;
    private final CTraderTokenLifecycleService tokenLifecycle;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final WebSocketConnector webSocketConnector;

    @Autowired
    public JsonWebSocketCTraderOpenApiClient(CTraderMarketDataProperties properties,
                                             CTraderTokenLifecycleService tokenLifecycle,
                                             ObjectMapper objectMapper) {
        this(properties, tokenLifecycle, objectMapper, null);
    }

    JsonWebSocketCTraderOpenApiClient(CTraderMarketDataProperties properties,
                                      CTraderTokenLifecycleService tokenLifecycle,
                                      ObjectMapper objectMapper,
                                      WebSocketConnector webSocketConnector) {
        this.properties = properties;
        this.tokenLifecycle = tokenLifecycle;
        this.objectMapper = objectMapper;
        this.webSocketConnector = webSocketConnector == null ? this::connectWebSocket : webSocketConnector;
    }

    @FunctionalInterface
    interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI endpoint, Duration timeout, WebSocket.Listener listener);
    }

    private CompletableFuture<WebSocket> connectWebSocket(URI endpoint, Duration timeout,
                                                           WebSocket.Listener listener) {
        return httpClient.newWebSocketBuilder().connectTimeout(timeout)
                .buildAsync(endpoint, listener);
    }

    @Override
    public List<CTraderSymbol> symbols() {
        return withAuthenticatedSession(session -> {
            JsonNode response = session.request(SYMBOL_LIST_REQUEST, SYMBOL_LIST_RESPONSE, accountPayload());
            List<CTraderSymbol> listed = new ArrayList<>();
            for (JsonNode node : response.path("symbol")) {
                listed.add(mapSymbol(node));
            }
            List<CTraderSymbol> incomplete = listed.stream().filter(symbol -> symbol.digits() <= 0).toList();
            if (incomplete.isEmpty()) return List.copyOf(listed);

            ObjectNode payload = accountPayload();
            var symbolIds = payload.putArray("symbolId");
            incomplete.forEach(symbol -> symbolIds.add(symbol.id()));
            Map<Long, CTraderSymbol> fallbackById = new HashMap<>();
            listed.forEach(symbol -> fallbackById.put(symbol.id(), symbol));
            Map<Long, CTraderSymbol> detailedById = new HashMap<>();
            for (JsonNode node : session.request(SYMBOL_BY_ID_REQUEST, SYMBOL_BY_ID_RESPONSE, payload).path("symbol")) {
                CTraderSymbol detail = mapSymbol(node, fallbackById.get(node.path("symbolId").asLong()));
                detailedById.put(detail.id(), detail);
            }
            return listed.stream().map(symbol -> detailedById.getOrDefault(symbol.id(), symbol)).toList();
        });
    }

    @Override
    public CTraderSymbol symbol(long symbolId) {
        return withAuthenticatedSession(session -> {
            return symbol(session, new CTraderSymbol(symbolId, "", 0, 0, 0, 0));
        });
    }

    @Override
    public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) {
        if (from == null || to == null || !from.isBefore(to) || count < 1) {
            throw new IllegalArgumentException("bounded trendbar range and positive count are required");
        }
        return CTraderTrendbarPagination.collect(from, to, count, properties.maxTrendbarPages(),
                properties.maxTrendbarRows(), periodDuration(period),
                request -> trendbarPage(symbolId, period, request.from(), request.to(), request.count())).stream().toList();
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
        long startedNanos = System.nanoTime();
        String diagnosticId = diagnosticId();
        log.info("CTRADER_OPEN_STREAM_ENTER diagnosticId={} accountId=unknown environment={} thread={} virtual={}",
                diagnosticId, properties.environment(), Thread.currentThread().getName(),
                Thread.currentThread().isVirtual());
        Session session = null;
        try {
            required(properties.clientId(), "clientId");
            required(properties.clientSecret(), "clientSecret");
            CTraderTokenStore.StoredCTraderToken token = tokenLifecycle.requireUsableToken(true);
            long accountId = token.record().externalAccountId();
            session = new Session(node -> dispatch(node, events), termination);
            try (token) {
                log.info("CTRADER_APPLICATION_AUTH_SEND diagnosticId={} accountId={} payloadType={}",
                        diagnosticId, accountId, APPLICATION_AUTH_REQUEST);
                session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                        objectMapper.createObjectNode().put("clientId", properties.clientId())
                                .put("clientSecret", properties.clientSecret()));
                log.info("CTRADER_APPLICATION_AUTH_RESPONSE diagnosticId={} accountId={} payloadType={}",
                        diagnosticId, accountId, APPLICATION_AUTH_RESPONSE);
                char[] access = token.material().copyAccessToken();
                try {
                JsonNode accounts = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                        objectMapper.createObjectNode().put("accessToken", new String(access)));
                validateEnvironment(accounts, accountId);
                log.info("CTRADER_ACCOUNT_AUTH_SEND diagnosticId={} accountId={} payloadType={}",
                        diagnosticId, accountId, ACCOUNT_AUTH_REQUEST);
                JsonNode accountAuth = session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                        objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                                .put("accessToken", new String(access)));
                log.info("CTRADER_ACCOUNT_AUTH_RESPONSE diagnosticId={} accountId={} payloadType={}",
                        diagnosticId, accountId, ACCOUNT_AUTH_RESPONSE);
                } finally {
                    Arrays.fill(access, '\0');
                }
            }
            log.info("CTRADER_OPEN_STREAM_EXIT diagnosticId={} accountId={} environment={} durationMs={}",
                    diagnosticId, accountId, properties.environment(), elapsedMillis(startedNanos));
            return new Stream(session, accountId);
        } catch (MarketDataProviderException exception) {
            closeQuietly(session);
            throw exception;
        } catch (RuntimeException exception) {
            closeQuietly(session);
            log.warn("CTRADER_OPEN_STREAM_ERROR diagnosticId={} accountId=unknown environment={} durationMs={} errorType={} message={}",
                    diagnosticId, properties.environment(), elapsedMillis(startedNanos),
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw new IllegalStateException("cTrader Market Data stream unavailable", exception);
        }
    }

    private void closeQuietly(Session session) {
        if (session == null) return;
        try {
            session.close();
        } catch (RuntimeException closeException) {
            log.warn("CTRADER_SESSION_CLOSE_AFTER_OPEN_FAILURE_ERROR errorType={} message={}",
                    closeException.getClass().getSimpleName(), closeException.getMessage());
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
                    boolean closeAvailable = bar.has("deltaClose");
                    if (symbolId > 0 && barTimestamp > 0 && !period.isBlank()
                            && bar.has("low") && bar.has("deltaOpen") && bar.has("deltaHigh")) {
                        events.accept(new CTraderLiveTrendbarEvent(symbolId, period,
                                decodeTrendbar(bar), Instant.ofEpochSecond(barTimestamp),
                                payload.has("bid") ? payload.path("bid").asLong() : null,
                                closeAvailable));
                    }
                }
            }
            case DEPTH_EVENT -> {
                long symbolId = payload.path("symbolId").asLong(0);
                if (symbolId <= 0) return;
                List<CTraderDepthQuote> quotes = new ArrayList<>();
                for (JsonNode quote : payload.path("newQuotes")) {
                    long id = quote.path("id").asLong(0);
                    if (id <= 0 || !quote.has("size")) continue;
                    quotes.add(new CTraderDepthQuote(id,
                            quote.has("bid") ? quote.path("bid").asLong() : null,
                            quote.has("ask") ? quote.path("ask").asLong() : null,
                            quote.path("size").asLong()));
                }
                List<Long> deleted = new ArrayList<>();
                for (JsonNode id : payload.path("deletedQuotes")) {
                    if (id.asLong(0) > 0) deleted.add(id.asLong());
                }
                events.accept(new CTraderDepthEvent(symbolId, quotes, deleted));
            }
            default -> { }
        }
    }

    static CTraderTrendbar decodeTrendbar(JsonNode node) {
        if (!node.isObject() || trendbarTimestamp(node) <= 0 || !node.has("low")
                || !node.has("deltaOpen") || !node.has("deltaHigh")) {
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

    private static Duration periodDuration(String period) {
        return switch (period) {
            case "M1" -> Duration.ofMinutes(1);
            case "M5" -> Duration.ofMinutes(5);
            case "M15" -> Duration.ofMinutes(15);
            case "M30" -> Duration.ofMinutes(30);
            case "H1" -> Duration.ofHours(1);
            case "H4" -> Duration.ofHours(4);
            case "H12" -> Duration.ofHours(12);
            case "D1" -> Duration.ofDays(1);
            case "W1" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException("unsupported cTrader trendbar period: " + period);
        };
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
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cTrader Market Data operation unavailable", exception);
        }
    }

    private CTraderSymbol mapSymbol(JsonNode node) {
        return mapSymbol(node, null);
    }

    private CTraderSymbol mapSymbol(JsonNode node, CTraderSymbol fallback) {
        long id = node.path("symbolId").asLong(fallback == null ? 0 : fallback.id());
        String name = node.path("symbolName").asText(fallback == null ? "" : fallback.name());
        if (!node.isObject() || id <= 0 || name.isBlank()) {
            throw new IllegalStateException("cTrader symbol metadata is incomplete");
        }
        return new CTraderSymbol(id, name, node.path("digits").asInt(fallback == null ? 0 : fallback.digits()),
                node.path("minVolume").asLong(fallback == null ? 0 : fallback.minVolume()),
                node.path("maxVolume").asLong(fallback == null ? 0 : fallback.maxVolume()),
                node.path("stepVolume").asLong(fallback == null ? 0 : fallback.stepVolume()));
    }

    private CTraderSymbol symbol(Session session, CTraderSymbol fallback) {
        ObjectNode payload = accountPayload();
        payload.putArray("symbolId").add(fallback.id());
        JsonNode symbols = session.request(SYMBOL_BY_ID_REQUEST,
                SYMBOL_BY_ID_RESPONSE, payload).path("symbol");
        JsonNode symbol = symbols.isArray() ? symbols.path(0) : symbols;
        return mapSymbol(symbol, fallback);
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
        private final String sessionId = java.util.UUID.randomUUID().toString();
        private final String diagnosticId = JsonWebSocketCTraderOpenApiClient.this.diagnosticId();
        private final Runnable termination;
        private final CTraderEventDispatcher eventDispatcher;
        private final AtomicBoolean terminationSignalled = new AtomicBoolean();
        private volatile WebSocket socket;

        private Session() { this(node -> { }, () -> { }); }

        private Session(Consumer<JsonNode> events, Runnable termination) {
            long startedNanos = System.nanoTime();
            this.termination = termination;
            this.eventDispatcher = new CTraderEventDispatcher("ctrader-events-" + sessionId,
                    events, this::signalTermination);
            log.info("CTRADER_SESSION_CREATE diagnosticId={} sessionId={} host={} port={} environment={}",
                    diagnosticId, sessionId, endpointHost(), endpointPort(), properties.environment());
            try {
                required(properties.websocketEndpoint(), "websocketEndpoint");
                if (!"wss".equalsIgnoreCase(URI.create(properties.websocketEndpoint()).getScheme())) {
                    throw new IllegalStateException("cTrader websocket endpoint must use WSS");
                }
                socket = webSocketConnector.connect(URI.create(properties.websocketEndpoint()), properties.requestTimeout(),
                                new Listener(messages, eventDispatcher::dispatch, this::signalTermination, diagnosticId, sessionId,
                                        endpointHost(), endpointPort()))
                        .orTimeout(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
                log.info("CTRADER_SESSION_CREATED diagnosticId={} sessionId={} host={} port={} durationMs={}",
                        diagnosticId, sessionId, endpointHost(), endpointPort(), elapsedMillis(startedNanos));
            } catch (Exception exception) {
                log.warn("CTRADER_SESSION_CREATE_ERROR diagnosticId={} sessionId={} host={} port={} durationMs={} errorType={} message={}",
                        diagnosticId, sessionId, endpointHost(), endpointPort(), elapsedMillis(startedNanos),
                        exception.getClass().getSimpleName(), exception.getMessage());
                eventDispatcher.close();
                throw new IllegalStateException("cTrader Open API transport unavailable", exception);
            }
        }

        private void signalTermination() {
            if (!terminationSignalled.compareAndSet(false, true)) return;
            eventDispatcher.close();
            WebSocket current = socket;
            try {
                if (current != null && !current.isOutputClosed()) {
                    current.sendClose(WebSocket.NORMAL_CLOSURE, "terminated");
                }
            } catch (RuntimeException exception) {
                log.warn("CTRADER_SESSION_TERMINATION_CLOSE_ERROR diagnosticId={} sessionId={} errorType={} message={}",
                        diagnosticId, sessionId, exception.getClass().getSimpleName(), exception.getMessage());
            } finally {
                termination.run();
            }
        }

        private JsonNode request(int requestType, int responseType, ObjectNode payload) {
            String clientMessageId = CTraderRequestCorrelation.create().clientMessageId();
            long startedNanos = System.nanoTime();
            ObjectNode message = objectMapper.createObjectNode().put("clientMsgId", clientMessageId)
                    .put("payloadType", requestType).set("payload", payload);
            log.info("CTRADER_REQUEST_REGISTERED diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={}",
                    diagnosticId, sessionId, requestType, responseType, clientMessageId);
            try {
                log.info("CTRADER_REQUEST_SEND_START diagnosticId={} sessionId={} requestPayloadType={} clientMsgId={}",
                        diagnosticId, sessionId, requestType, clientMessageId);
                socket.sendText(message.toString(), true)
                        .orTimeout(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
                log.info("CTRADER_REQUEST_SEND_COMPLETE diagnosticId={} sessionId={} requestPayloadType={} clientMsgId={} durationMs={}",
                        diagnosticId, sessionId, requestType, clientMessageId, elapsedMillis(startedNanos));
            } catch (RuntimeException exception) {
                log.warn("CTRADER_REQUEST_FAILURE diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={} phase=send errorType={} message={}",
                        diagnosticId, sessionId, requestType, responseType, clientMessageId,
                        exception.getClass().getSimpleName(), exception.getMessage());
                throw exception;
            }
            long deadline = System.nanoTime() + properties.requestTimeout().toNanos();
            log.info("CTRADER_REQUEST_WAIT_START diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={}",
                    diagnosticId, sessionId, requestType, responseType, clientMessageId);
            try {
                while (System.nanoTime() < deadline) {
                    long remainingMillis = Math.max(1, Math.min(1000,
                            Duration.ofNanos(deadline - System.nanoTime()).toMillis()));
                    String raw = messages.poll(remainingMillis, TimeUnit.MILLISECONDS);
                    if (raw == null) continue;
                    JsonNode response = objectMapper.readTree(raw);
                    if (clientMessageId.equals(response.path("clientMsgId").asText())
                            && response.path("payloadType").asInt() == responseType) {
                        if (response.path("errorCode").isTextual()
                                || response.path("payload").path("errorCode").isTextual()) {
                            String errorCode = response.path("errorCode").asText(
                                    response.path("payload").path("errorCode").asText("unknown"));
                            String description = response.path("description").asText(
                                    response.path("payload").path("description").asText(""));
                            throw new IllegalStateException("cTrader provider rejected the request: "
                                    + errorCode + (description.isBlank() ? "" : " (" + description + ")"));
                        }
                        log.info("CTRADER_REQUEST_RESPONSE_MATCHED diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={} durationMs={}",
                                diagnosticId, sessionId, requestType, responseType, clientMessageId, elapsedMillis(startedNanos));
                        return response.path("payload");
                    }
                    log.info("CTRADER_REQUEST_FRAME_IGNORED diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={} receivedPayloadType={} receivedClientMsgId={}",
                            diagnosticId, sessionId, requestType, responseType, clientMessageId,
                            response.path("payloadType").asInt(), response.path("clientMsgId").asText(""));
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
                log.warn("CTRADER_REQUEST_TIMEOUT diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={} durationMs={}",
                        diagnosticId, sessionId, requestType, responseType, clientMessageId, elapsedMillis(startedNanos));
                throw new MarketDataProviderException(
                        MarketDataProviderException.PROVIDER_TIMEOUT,
                        "cTrader Open API response timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("CTRADER_REQUEST_FAILURE diagnosticId={} sessionId={} requestPayloadType={} expectedResponseType={} clientMsgId={} phase=wait errorType={} message={}",
                        diagnosticId, sessionId, requestType, responseType, clientMessageId,
                        exception.getClass().getSimpleName(), exception.getMessage());
                throw new IllegalStateException("cTrader Open API request interrupted", exception);
            }
        }

        @Override public void close() {
            eventDispatcher.close();
            WebSocket current = socket;
            if (current != null && !current.isOutputClosed()) {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            }
        }
    }

    private final class Stream implements CTraderStream {
        private final Session session;
        private final long accountId;

        private Stream(Session session, long accountId) { this.session = session; this.accountId = accountId; }

        @Override public void subscribeSpots(long symbolId) {
            subscribeSpots(List.of(symbolId));
        }

        @Override public void subscribeSpots(List<Long> symbolIds) {
            if (symbolIds == null || symbolIds.isEmpty()) return;
            long startedNanos = System.nanoTime();
            log.info("CTRADER_SPOT_SUBSCRIPTION_CREATE diagnosticId={} sessionId={} accountId={} symbolIds={} subscribeToSpotTimestamp=true payloadType={}",
                    session.diagnosticId, session.sessionId, accountId, symbolIds, SUBSCRIBE_SPOTS_REQUEST);
            ObjectNode payload = spotSubscriptionPayload(objectMapper, accountId, symbolIds);
            log.info("CTRADER_SPOT_SUBSCRIPTION_SERIALIZE diagnosticId={} sessionId={} accountId={} symbolIds={} payloadType={}",
                    session.diagnosticId, session.sessionId, accountId, symbolIds, SUBSCRIBE_SPOTS_REQUEST);
            try {
                session.request(SUBSCRIBE_SPOTS_REQUEST, SUBSCRIBE_SPOTS_RESPONSE, payload);
                log.info("CTRADER_SPOT_SUBSCRIPTION_RESPONSE diagnosticId={} sessionId={} accountId={} symbolIds={} payloadType={} durationMs={}",
                        session.diagnosticId, session.sessionId, accountId, symbolIds, SUBSCRIBE_SPOTS_RESPONSE,
                        elapsedMillis(startedNanos));
            } catch (RuntimeException exception) {
                log.warn("CTRADER_SPOT_SUBSCRIPTION_FAILURE diagnosticId={} sessionId={} accountId={} symbolIds={} durationMs={} errorType={} message={}",
                        session.diagnosticId, session.sessionId, accountId, symbolIds, elapsedMillis(startedNanos),
                        exception.getClass().getSimpleName(), exception.getMessage());
                throw exception;
            }
        }

        @Override public void unsubscribeSpots(long symbolId) {
            unsubscribeSpots(List.of(symbolId));
        }

        @Override public void unsubscribeSpots(List<Long> symbolIds) {
            if (symbolIds == null || symbolIds.isEmpty()) return;
            session.request(UNSUBSCRIBE_SPOTS_REQUEST, UNSUBSCRIBE_SPOTS_RESPONSE,
                    spotUnsubscriptionPayload(objectMapper, accountId, symbolIds));
        }

        @Override public void subscribeLiveTrendbar(long symbolId, String period) {
            session.request(SUBSCRIBE_LIVE_TRENDBAR_REQUEST, SUBSCRIBE_LIVE_TRENDBAR_RESPONSE,
                    accountPayload().put("symbolId", symbolId).put("period", periodValue(period)));
        }

        @Override public void unsubscribeLiveTrendbar(long symbolId, String period) {
            session.request(UNSUBSCRIBE_LIVE_TRENDBAR_REQUEST, UNSUBSCRIBE_LIVE_TRENDBAR_RESPONSE,
                    accountPayload().put("symbolId", symbolId).put("period", periodValue(period)));
        }

        @Override public void subscribeDepth(long symbolId) {
            subscribeDepth(List.of(symbolId));
        }

        @Override public void subscribeDepth(List<Long> symbolIds) {
            if (symbolIds == null || symbolIds.isEmpty()) return;
            session.request(SUBSCRIBE_DEPTH_QUOTES_REQUEST, SUBSCRIBE_DEPTH_QUOTES_RESPONSE,
                    depthSubscriptionPayload(objectMapper, accountId, symbolIds));
        }

        @Override public void unsubscribeDepth(long symbolId) {
            unsubscribeDepth(List.of(symbolId));
        }

        @Override public void unsubscribeDepth(List<Long> symbolIds) {
            if (symbolIds == null || symbolIds.isEmpty()) return;
            session.request(UNSUBSCRIBE_DEPTH_QUOTES_REQUEST, UNSUBSCRIBE_DEPTH_QUOTES_RESPONSE,
                    depthSubscriptionPayload(objectMapper, accountId, symbolIds));
        }
        @Override public void close() { session.close(); }

        private ObjectNode accountPayload() { return objectMapper.createObjectNode().put("ctidTraderAccountId", accountId); }
    }

    static ObjectNode spotSubscriptionPayload(ObjectMapper objectMapper, long accountId, long symbolId) {
        return spotSubscriptionPayload(objectMapper, accountId, List.of(symbolId));
    }

    static ObjectNode spotSubscriptionPayload(ObjectMapper objectMapper, long accountId, List<Long> symbolIds) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("ctidTraderAccountId", accountId)
                .put("subscribeToSpotTimestamp", true);
        var symbols = payload.putArray("symbolId");
        symbolIds.forEach(symbols::add);
        return payload;
    }

    static ObjectNode spotUnsubscriptionPayload(ObjectMapper objectMapper, long accountId, List<Long> symbolIds) {
        ObjectNode payload = objectMapper.createObjectNode().put("ctidTraderAccountId", accountId);
        var symbols = payload.putArray("symbolId");
        symbolIds.forEach(symbols::add);
        return payload;
    }

    static ObjectNode depthSubscriptionPayload(ObjectMapper objectMapper, long accountId, List<Long> symbolIds) {
        ObjectNode payload = objectMapper.createObjectNode().put("ctidTraderAccountId", accountId);
        var symbols = payload.putArray("symbolId");
        symbolIds.forEach(symbols::add);
        return payload;
    }

    static final class Listener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final Consumer<JsonNode> events;
        private final Runnable termination;
        private final String diagnosticId;
        private final String sessionId;
        private final String host;
        private final int port;
        private final StringBuilder text = new StringBuilder();

        Listener(BlockingQueue<String> messages, Consumer<JsonNode> events, Runnable termination,
                 String diagnosticId, String sessionId, String host, int port) {
            this.messages = messages;
            this.events = events;
            this.termination = termination;
            this.diagnosticId = diagnosticId;
            this.sessionId = sessionId;
            this.host = host;
            this.port = port;
        }

        @Override public void onOpen(WebSocket webSocket) {
            log.info("CTRADER_CONNECT_SUCCESS diagnosticId={} sessionId={} host={} port={}",
                    diagnosticId, sessionId, host, port);
            webSocket.request(1);
        }

        @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String value = text.toString();
                text.setLength(0);
                log.info("CTRADER_FRAME_RECEIVED diagnosticId={} sessionId={} frameSize={}",
                        diagnosticId, sessionId, value.length());
                try {
                    JsonNode node = new ObjectMapper().readTree(value);
                    int payloadType = node.path("payloadType").asInt();
                    log.info("CTRADER_PAYLOAD_DECODED diagnosticId={} sessionId={} payloadType={} clientMsgId={}",
                            diagnosticId, sessionId, payloadType, node.path("clientMsgId").asText(""));
                    if (payloadType == SPOT_EVENT || payloadType == DEPTH_EVENT) {
                        JsonNode payload = node.path("payload");
                        log.info("CTRADER_MARKET_EVENT_RECEIVED diagnosticId={} sessionId={} payloadType={} symbolId={} timestamp={} hasBid={} hasAsk={}",
                                diagnosticId, sessionId, payloadType, payload.path("symbolId").asLong(0),
                                payload.path("timestamp").asLong(0),
                                payload.has("bid"), payload.has("ask"));
                        events.accept(node);
                    } else {
                        enqueueOrTerminate(value);
                    }
                } catch (RuntimeException exception) {
                    log.warn("CTRADER_FRAME_DECODE_ERROR diagnosticId={} sessionId={} frameSize={} errorType={} message={}",
                            diagnosticId, sessionId, value.length(), exception.getClass().getSimpleName(), exception.getMessage());
                    enqueueOrTerminate(value);
                }
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
            log.warn("CTRADER_SOCKET_CLOSED diagnosticId={} sessionId={} statusCode={} reason={}",
                    diagnosticId, sessionId, statusCode, reason);
            termination.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket socket, Throwable error) {
            log.warn("CTRADER_SOCKET_ERROR diagnosticId={} sessionId={} errorType={} message={}",
                    diagnosticId, sessionId, error.getClass().getSimpleName(), error.getMessage());
            termination.run();
        }
    }

    private String diagnosticId() {
        String value = MDC.get("ctraderDiagnosticId");
        return value == null ? "none" : value;
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String endpointHost() {
        return URI.create(properties.websocketEndpoint()).getHost();
    }

    private int endpointPort() {
        URI uri = URI.create(properties.websocketEndpoint());
        return uri.getPort() > 0 ? uri.getPort() : 443;
    }
}
