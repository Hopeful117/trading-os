package com.hope.trading.broker_service.ctrader.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.*;

@Component
public class JsonWebSocketCTraderOpenApiClient implements CTraderOpenApiClient {
    private static final Logger log = LoggerFactory.getLogger(JsonWebSocketCTraderOpenApiClient.class);

    private final CTraderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JsonWebSocketCTraderOpenApiClient(CTraderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> discoverAuthorizedAccountIds(CTraderToken token) {
        try (Session session = new Session()) {
            session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("clientId", required(properties.clientId()))
                            .put("clientSecret", required(properties.clientSecret())));
            JsonNode response = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("accessToken", token.accessToken()));
            List<String> ids = new ArrayList<>();
            for (JsonNode account : response.path("ctidTraderAccount")) {
                if (account.hasNonNull("ctidTraderAccountId")) {
                    String accountId = account.path("ctidTraderAccountId").asText();
                    ids.add(accountId);
                    log.info("ctrader_authorized_account accountId={} traderLogin={} isLive={}",
                            accountId,
                            account.path("traderLogin").isMissingNode() ? null : account.path("traderLogin").asText(),
                            account.path("isLive").isMissingNode() ? null : account.path("isLive").asBoolean());
                }
            }
            return List.copyOf(ids);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader account discovery failed", exception);
        }
    }

    @Override
    public void authorizeAccount(CTraderToken token, String externalAccountId) {
        try (Session session = new Session()) {
            session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("clientId", required(properties.clientId()))
                            .put("clientSecret", required(properties.clientSecret())));
            session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("ctidTraderAccountId", Long.parseLong(externalAccountId))
                            .put("accessToken", token.accessToken()));
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader account authorization failed", exception);
        }
    }

    @Override
    public CTraderReadOnlyObservation readOnlyObservation(CTraderToken token, String externalAccountId,
                                                           java.time.Instant from, java.time.Instant to) {
        Instant started = Instant.now();
        try (Session session = new Session()) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(externalAccountId);
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);

            JsonNode trader = requireAccountResponse(session.request(TRADER_REQUEST, TRADER_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            JsonNode assets = requireAccountResponse(session.request(ASSET_LIST_REQUEST, ASSET_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            JsonNode symbols = requireAccountResponse(session.request(SYMBOL_LIST_REQUEST, SYMBOL_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            JsonNode reconcile = requireAccountResponse(session.request(RECONCILE_REQUEST, RECONCILE_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("returnProtectionOrders", true)), accountId);
            JsonNode pnl = requireAccountResponse(session.request(POSITION_PNL_REQUEST, POSITION_PNL_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            JsonNode deals = requireAccountResponse(session.request(DEAL_LIST_REQUEST, DEAL_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("fromTimestamp", from.toEpochMilli())
                            .put("toTimestamp", to.toEpochMilli())
                            .put("maxRows", 1000)), accountId);

            return mapObservation(externalAccountId, trader, assets, symbols, reconcile, pnl, deals, started);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader read-only synchronization failed", exception);
        }
    }

    private void authenticateApplication(Session session) {
        session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                objectMapper.createObjectNode()
                        .put("clientId", required(properties.clientId()))
                        .put("clientSecret", required(properties.clientSecret())));
    }

    private CTraderReadOnlyObservation mapObservation(String externalAccountId, JsonNode trader,
                                                       JsonNode assets, JsonNode symbols, JsonNode reconcile,
                                                       JsonNode pnl, JsonNode deals, Instant started) {
        List<String> reasons = new java.util.ArrayList<>();
        requireObject(trader, "trader", reasons);
        requireArray(assets, "asset", reasons);
        requireArray(symbols, "symbol", reasons);
        requireArray(reconcile, "position", reasons);
        requireArray(reconcile, "order", reasons);
        requireArray(pnl, "positionUnrealizedPnl", reasons);
        requireArray(deals, "deal", reasons);

        int moneyDigits = trader.path("trader").path("moneyDigits").asInt(0);
        JsonNode traderNode = trader.path("trader");
        BigDecimal balance = money(traderNode.path("balance"), moneyDigits);
        if (balance == null || !traderNode.has("depositAssetId")) {
            throw new CTraderAuthenticationException("cTrader account balance facts are incomplete");
        }
        Map<String, String> assetNames = new HashMap<>();
        for (JsonNode asset : assets.path("asset")) {
            assetNames.put(asset.path("assetId").asText(), asset.path("name").asText(""));
        }
        String valuationAsset = assetNames.get(traderNode.path("depositAssetId").asText());
        Map<String, String> symbolNames = new HashMap<>();
        for (JsonNode symbol : symbols.path("symbol")) {
            symbolNames.put(symbol.path("symbolId").asText(), symbol.path("symbolName").asText(""));
        }
        if (valuationAsset == null || valuationAsset.isBlank()) reasons.add("VALUATION_ASSET_UNAVAILABLE");

        List<CTraderReadOnlyObservation.Position> positions = new java.util.ArrayList<>();
        BigDecimal usedMargin = BigDecimal.ZERO;
        for (JsonNode position : reconcile.path("position")) {
            JsonNode tradeData = position.path("tradeData");
            String symbolId = tradeData.path("symbolId").asText();
            String instrument = symbolNames.get(symbolId);
            if (instrument == null || instrument.isBlank()) {
                reasons.add("SYMBOL_MAPPING_UNAVAILABLE:" + symbolId);
                continue;
            }
            BigDecimal rawVolume = decimal(tradeData.path("volume"));
            if (rawVolume == null) {
                reasons.add("POSITION_VOLUME_UNAVAILABLE:" + position.path("positionId").asText());
                continue;
            }
            BigDecimal quantity = rawVolume.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
            if (isSell(tradeData.path("tradeSide"))) quantity = quantity.negate();
            Instant observedAt = instant(position.path("utcLastUpdateTimestamp"), started);
            BigDecimal entryPrice = position.path("price").isNumber() ? position.path("price").decimalValue() : null;
            if (entryPrice == null || entryPrice.signum() <= 0) {
                reasons.add("POSITION_ENTRY_PRICE_UNAVAILABLE:" + position.path("positionId").asText());
                continue;
            }
            BigDecimal positionMargin = money(position.path("usedMargin"), position.path("moneyDigits").asInt(moneyDigits));
            if (positionMargin == null) {
                reasons.add("POSITION_MARGIN_UNAVAILABLE:" + position.path("positionId").asText());
            } else {
                usedMargin = usedMargin.add(positionMargin);
            }
            BigDecimal stopLoss = position.has("stopLoss") && position.path("stopLoss").isNumber()
                    ? position.path("stopLoss").decimalValue() : null;
            if (position.has("stopLoss") && stopLoss != null && stopLoss.signum() <= 0) {
                if (stopLoss.signum() < 0) {
                    reasons.add("POSITION_STOP_LOSS_INVALID:" + position.path("positionId").asText());
                }
                stopLoss = null;
            }
            positions.add(new CTraderReadOnlyObservation.Position(instrument, quantity,
                    entryPrice, observedAt, position.path("positionId").asText(), positionMargin, stopLoss));
        }

        List<CTraderReadOnlyObservation.Order> orders = new java.util.ArrayList<>();
        for (JsonNode order : reconcile.path("order")) {
            JsonNode tradeData = order.path("tradeData");
            BigDecimal rawQuantity = decimal(tradeData.path("volume"));
            BigDecimal rawFilled = decimal(order.path("executedVolume"));
            if (rawQuantity == null || rawFilled == null || !order.has("orderId")) {
                reasons.add("ORDER_FACTS_UNAVAILABLE:" + order.path("orderId").asText());
                continue;
            }
            BigDecimal quantity = rawQuantity.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
            BigDecimal filled = rawFilled.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
            orders.add(new CTraderReadOnlyObservation.Order(order.path("orderId").asText(),
                    tradeData.path("clientOrderId").asText(null), order.path("orderStatus").asText("UNKNOWN"),
                    quantity, filled, instant(order.path("utcLastUpdateTimestamp"), started)));
        }

        List<CTraderReadOnlyObservation.Deal> dealFacts = new java.util.ArrayList<>();
        for (JsonNode deal : deals.path("deal")) {
            String symbolId = deal.path("symbolId").asText();
            String instrument = symbolNames.get(symbolId);
            if (instrument == null || instrument.isBlank()) {
                reasons.add("DEAL_SYMBOL_MAPPING_UNAVAILABLE:" + symbolId);
                continue;
            }
            int dealDigits = deal.path("moneyDigits").asInt(moneyDigits);
            BigDecimal executionPrice = deal.path("executionPrice").isNumber()
                    ? deal.path("executionPrice").decimalValue() : null;
            if (executionPrice == null || executionPrice.signum() <= 0) {
                reasons.add("DEAL_EXECUTION_PRICE_UNAVAILABLE:" + deal.path("dealId").asText());
                continue;
            }
            BigDecimal rawFilledVolume = decimal(deal.path("filledVolume"));
            if (rawFilledVolume == null || !deal.has("dealId")) {
                reasons.add("DEAL_FACTS_UNAVAILABLE:" + deal.path("dealId").asText());
                continue;
            }
            dealFacts.add(new CTraderReadOnlyObservation.Deal(deal.path("dealId").asText(), instrument,
                    rawFilledVolume.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64),
                    deal.path("tradeSide").asText("UNKNOWN"), executionPrice, money(deal.path("commission"), dealDigits),
                    money(deal.path("closePositionDetail").path("swap"), dealDigits),
                    closePnl(deal.path("closePositionDetail"), dealDigits),
                    instant(deal.path("executionTimestamp"), started)));
        }

        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        boolean pnlComplete = true;
        for (JsonNode item : pnl.path("positionUnrealizedPnl")) {
            BigDecimal positionPnl = money(item.path("netUnrealizedPnl"), pnl.path("moneyDigits").asInt(moneyDigits));
            if (positionPnl == null) {
                pnlComplete = false;
            } else {
                unrealizedPnl = unrealizedPnl.add(positionPnl);
            }
        }
        BigDecimal equity = pnlComplete ? balance.add(unrealizedPnl) : null;
        if (!pnlComplete) reasons.add("UNREALIZED_PNL_UNAVAILABLE");
        Instant providerObservedAt = latestProviderTimestamp(trader, reconcile, deals, started, reasons);
        boolean complete = reasons.isEmpty() && equity != null && !deals.path("hasMore").asBoolean(false)
                && valuationAsset != null && !valuationAsset.isBlank();
        if (deals.path("hasMore").asBoolean(false)) reasons.add("DEAL_HISTORY_TRUNCATED");
        Instant completed = Instant.now();
        return new CTraderReadOnlyObservation(externalAccountId, valuationAsset == null ? "UNKNOWN" : valuationAsset,
                balance, equity, usedMargin, null, positions, orders, dealFacts,
                started, providerObservedAt, completed, complete, List.copyOf(new LinkedHashSet<>(reasons)));
    }

    private BigDecimal closePnl(JsonNode detail, int moneyDigits) {
        return detail.isObject() && detail.has("grossProfit") ? money(detail.path("grossProfit"), moneyDigits) : null;
    }

    private BigDecimal money(JsonNode value, int digits) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        return value.decimalValue().movePointLeft(Math.max(0, digits));
    }

    private BigDecimal decimal(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.decimalValue();
    }

    private Instant instant(JsonNode value, Instant fallback) {
        return value != null && value.canConvertToLong() && value.asLong() > 0
                ? Instant.ofEpochMilli(value.asLong()) : fallback;
    }

    private boolean isSell(JsonNode side) {
        return "SELL".equalsIgnoreCase(side.asText()) || side.asInt() == 2;
    }

    private JsonNode requireAccountResponse(JsonNode response, long expectedAccountId) {
        JsonNode actual = response.path("ctidTraderAccountId");
        if (!actual.canConvertToLong() || actual.asLong() != expectedAccountId) {
            throw new CTraderAuthenticationException("cTrader response account identity mismatch");
        }
        return response;
    }

    private void requireArray(JsonNode response, String field, List<String> reasons) {
        if (!response.has(field) || !response.path(field).isArray()) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:" + field);
        }
    }

    private void requireObject(JsonNode response, String field, List<String> reasons) {
        if (!response.has(field) || !response.path(field).isObject()) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:" + field);
        }
    }

    private Instant latestProviderTimestamp(JsonNode trader, JsonNode reconcile, JsonNode deals,
                                            Instant fallback, List<String> reasons) {
        Instant latest = null;
        for (JsonNode position : reconcile.path("position")) {
            latest = latest(latest, instantOrNull(position.path("utcLastUpdateTimestamp")));
        }
        for (JsonNode order : reconcile.path("order")) {
            latest = latest(latest, instantOrNull(order.path("utcLastUpdateTimestamp")));
        }
        for (JsonNode deal : deals.path("deal")) {
            latest = latest(latest, instantOrNull(deal.path("utcLastUpdateTimestamp")));
            latest = latest(latest, instantOrNull(deal.path("executionTimestamp")));
        }
        if (latest == null) {
            reasons.add("PROVIDER_TIMESTAMP_UNAVAILABLE");
            return fallback;
        }
        return latest;
    }

    private Instant latest(Instant current, Instant candidate) {
        return candidate == null || (current != null && !candidate.isAfter(current)) ? current : candidate;
    }

    private Instant instantOrNull(JsonNode value) {
        return value != null && value.canConvertToLong() && value.asLong() > 0
                ? Instant.ofEpochMilli(value.asLong()) : null;
    }

    private void validateEnvironment() {
        String environment = required(properties.environment()).toLowerCase(java.util.Locale.ROOT);
        String endpoint = required(properties.websocketEndpoint()).toLowerCase(java.util.Locale.ROOT);
        boolean demo = environment.equals("demo");
        boolean endpointDemo = endpoint.contains("demo.ctraderapi.com");
        boolean endpointLive = endpoint.contains("live.ctraderapi.com");
        if ((demo && !endpointDemo) || (!demo && !endpointLive)) {
            throw new CTraderAuthenticationException("cTrader endpoint does not match configured environment");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new CTraderAuthenticationException("cTrader application is not configured");
        return value;
    }

    private final class Session implements AutoCloseable {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final WebSocket socket;

        private Session() {
            try {
                socket = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(URI.create(properties.websocketEndpoint()), new Listener(messages))
                        .orTimeout(15, TimeUnit.SECONDS).join();
            } catch (Exception exception) {
                throw new CTraderAuthenticationException("cTrader Open API transport is unavailable", exception);
            }
        }

        private JsonNode request(int requestType, int responseType, ObjectNode payload) {
            String clientMsgId = UUID.randomUUID().toString();
            ObjectNode message = objectMapper.createObjectNode()
                    .put("clientMsgId", clientMsgId)
                    .put("payloadType", requestType)
                    .set("payload", payload);
            socket.sendText(message.toString(), true).join();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try {
                while (System.nanoTime() < deadline) {
                    String raw = messages.poll(1, TimeUnit.SECONDS);
                    if (raw == null) continue;
                    JsonNode response = objectMapper.readTree(raw);
                    if (response.path("clientMsgId").asText().equals(clientMsgId)
                            && response.path("payloadType").asInt() == responseType) {
                        return response.path("payload");
                    }
                    if (response.path("errorCode").isTextual()
                            || response.path("payload").path("errorCode").isTextual()) {
                        throw new CTraderAuthenticationException("cTrader Open API rejected the request");
                    }
                }
                throw new CTraderAuthenticationException("cTrader Open API response timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CTraderAuthenticationException("cTrader Open API request interrupted", exception);
            } catch (CTraderAuthenticationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CTraderAuthenticationException("cTrader Open API response is invalid", exception);
            }
        }

        @Override
        public void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final StringBuilder text = new StringBuilder();

        private Listener(BlockingQueue<String> messages) {
            this.messages = messages;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.offer(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }
    }
}
