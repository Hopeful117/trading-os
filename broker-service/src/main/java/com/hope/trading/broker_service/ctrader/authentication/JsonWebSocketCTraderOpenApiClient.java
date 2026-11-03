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
import com.hope.trading.broker_service.ctrader.execution.CTraderExecutionInstrumentContract;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;
import com.hope.trading.ctrader.openapi.codec.CTraderPriceDecoder;
import static com.hope.trading.ctrader.openapi.protocol.CTraderPayloadTypes.*;

@Component
public class JsonWebSocketCTraderOpenApiClient implements CTraderOpenApiClient {
    private static final Logger log = LoggerFactory.getLogger(JsonWebSocketCTraderOpenApiClient.class);

    private final CTraderProperties properties;
    private final CTraderRequestSafetyGuard safety;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JsonWebSocketCTraderOpenApiClient(CTraderProperties properties, CTraderRequestSafetyGuard safety,
                                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.safety = safety;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> discoverAuthorizedAccountIds(CTraderToken token) {
        try (Session session = new Session(null)) {
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
        try (Session session = new Session(externalAccountId)) {
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
    public boolean isDemoAccount(CTraderToken token, String externalAccountId) {
        try (Session session = new Session(null)) {
            authenticateApplication(session);
            JsonNode response = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("accessToken", token.accessToken()));
            for (JsonNode account : response.path("ctidTraderAccount")) {
                if (externalAccountId.equals(account.path("ctidTraderAccountId").asText())) {
                    if (!account.has("isLive")) {
                        throw new CTraderAuthenticationException("cTrader account environment is unavailable");
                    }
                    boolean providerLive = account.path("isLive").asBoolean();
                    if (!providerLive) return true;
                    // FTMO FREE accounts are simulated even when cTrader reports isLive=true.
                    // Their provider transport can nevertheless be the live endpoint.
                    return "FTMO_FREE".equalsIgnoreCase(properties.accountProgram())
                            && ("demo".equalsIgnoreCase(properties.environment())
                            || "live".equalsIgnoreCase(properties.environment()));
                }
            }
            throw new CTraderAuthenticationException("cTrader account is not authorized");
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader account environment discovery failed", exception);
        }
    }

    @Override
    public CTraderOrderResponse submitOrder(CTraderToken token, CTraderOrderRequest request) {
        safety.requireExecutionEnabled();
        if (request.stopLossPrice() == null) {
            throw new CTraderAuthenticationException("cTrader demo execution requires a protective stop");
        }
        try (Session session = new Session(request.externalAccountId())) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(request.externalAccountId());
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);
            CTraderExecutionInstrumentContract symbol = resolveSymbol(session, accountId, request.instrument());
            final long providerVolume;
            final long relativeStopLoss;
            final Long relativeTakeProfit;
            try {
                providerVolume = symbol.providerVolume(request.quantity());
                if ("MARKET".equalsIgnoreCase(request.orderType())) {
                    relativeStopLoss = symbol.relativeStopLoss(request.referencePrice(), request.stopLossPrice(), request.side());
                    relativeTakeProfit = request.takeProfitPrices() != null && !request.takeProfitPrices().isEmpty()
                            ? symbol.relativeTakeProfit(request.referencePrice(), request.takeProfitPrices().getFirst(), request.side())
                            : null;
                } else {
                    relativeStopLoss = 0;
                    relativeTakeProfit = null;
                }
            } catch (IllegalArgumentException exception) {
                throw new CTraderAuthenticationException(exception.getMessage(), exception);
            }
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("ctidTraderAccountId", accountId)
                    .put("symbolId", symbol.symbolId())
                    .put("orderType", orderType(request.orderType()))
                    .put("tradeSide", tradeSide(request.side()))
                    .put("volume", providerVolume)
                    .put("clientOrderId", request.clientOrderId());
            if ("MARKET".equalsIgnoreCase(request.orderType())) {
                payload.put("relativeStopLoss", relativeStopLoss);
                if (relativeTakeProfit != null) payload.put("relativeTakeProfit", relativeTakeProfit);
            }
            if (request.takeProfitPrices() != null && request.takeProfitPrices().size() > 1) {
                throw new CTraderAuthenticationException("CTRADER_UNSUPPORTED_TAKE_PROFIT_CONFIGURATION");
            }
            if (request.takeProfitPrices() != null && !request.takeProfitPrices().isEmpty()) {
                if (!"MARKET".equalsIgnoreCase(request.orderType())) {
                    try {
                        payload.put("takeProfit", symbol.price(request.takeProfitPrices().getFirst(), "TAKE_PROFIT").doubleValue());
                    } catch (IllegalArgumentException exception) {
                        throw new CTraderAuthenticationException(exception.getMessage(), exception);
                    }
                }
            }
            if (request.limitPrice() != null) {
                try {
                    payload.put("limitPrice", symbol.price(request.limitPrice(), "LIMIT_PRICE").doubleValue());
                } catch (IllegalArgumentException exception) {
                    throw new CTraderAuthenticationException(exception.getMessage(), exception);
                }
            }
            JsonNode response = requireAccountResponse(session.request(NEW_ORDER_REQUEST, EXECUTION_EVENT, payload), accountId);
            String orderId = response.path("orderId").asText("");
            if (orderId.isBlank()) throw new CTraderAuthenticationException("cTrader order response did not contain an order id");
            return new CTraderOrderResponse(orderId);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader order submission failed", exception);
        }
    }

    @Override
    public CTraderClosePositionResponse closePosition(CTraderToken token, CTraderClosePositionRequest request) {
        safety.requireExecutionEnabled();
        try (Session session = new Session(request.externalAccountId())) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(request.externalAccountId());
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);
            CTraderExecutionInstrumentContract symbol = resolveSymbol(session, accountId, request.instrument());
            final long providerVolume;
            try {
                providerVolume = symbol.providerVolume(request.quantity());
            } catch (IllegalArgumentException exception) {
                throw new CTraderAuthenticationException(exception.getMessage(), exception);
            }
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("ctidTraderAccountId", accountId)
                    .put("positionId", request.positionId())
                    .put("volume", providerVolume);
            JsonNode response = requireAccountResponse(session.request(CLOSE_POSITION_REQUEST,
                    EXECUTION_EVENT, payload), accountId);
            String externalOrderId = response.path("order").path("orderId").asText("");
            String dealId = response.path("deal").path("dealId").asText("");
            String correlationId = !dealId.isBlank() ? dealId : response.path("position").path("positionId").asText("");
            if (externalOrderId.isBlank() && correlationId.isBlank()) {
                throw new CTraderAuthenticationException("cTrader close response did not contain execution identity");
            }
            return new CTraderClosePositionResponse(
                    externalOrderId.isBlank() ? correlationId : externalOrderId,
                    request.clientRequestId());
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader position close submission failed", exception);
        }
    }

    private CTraderExecutionInstrumentContract resolveSymbol(Session session, long accountId, String instrument) {
        ResolvedSymbol resolved = resolveSymbolNodes(session, accountId, instrument);
        JsonNode listed = resolved.listed();
        JsonNode details = resolved.details();
        try {
            return new CTraderExecutionInstrumentContract(
                    resolved.symbolId(),
                    listed.path("symbolName").asText(),
                    details.path("digits").asInt(listed.path("digits").asInt(0)),
                    details.path("pipPosition").asInt(listed.path("pipPosition").asInt(0)),
                    details.path("minVolume").asLong(0),
                    details.path("maxVolume").asLong(0),
                    details.path("stepVolume").asLong(0),
                    optionalPositiveLong(details, "lotSize"),
                    optionalNonNegativeLong(details, "slDistance"),
                    optionalNonNegativeLong(details, "tpDistance"),
                    details.path("distanceSetIn").isTextual() ? details.path("distanceSetIn").asText() : null);
        } catch (IllegalArgumentException exception) {
            throw new CTraderAuthenticationException(exception.getMessage(), exception);
        }
    }

    private ResolvedSymbol resolveSymbolNodes(Session session, long accountId, String instrument) {
        JsonNode response = requireAccountResponse(session.request(SYMBOL_LIST_REQUEST, SYMBOL_LIST_RESPONSE,
                objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
        JsonNode listed = selectListedSymbol(response, instrument);
        long symbolId = listed.path("symbolId").asLong(0);
        if (symbolId <= 0) throw new CTraderAuthenticationException("CTRADER_UNKNOWN_INSTRUMENT");
        ObjectNode symbolRequest = objectMapper.createObjectNode()
                .put("ctidTraderAccountId", accountId);
        symbolRequest.putArray("symbolId").add(symbolId);
        JsonNode detailsResponse = requireAccountResponse(session.request(SYMBOL_BY_ID_REQUEST,
                SYMBOL_BY_ID_RESPONSE, symbolRequest), accountId);
        JsonNode details = detailsResponse.path("symbol").isArray()
                ? detailsResponse.path("symbol").path(0)
                : detailsResponse.path("symbol");
        if (!details.isObject()) throw new CTraderAuthenticationException("CTRADER_METADATA_UNAVAILABLE");
        return new ResolvedSymbol(symbolId, listed, details);
    }

    JsonNode selectListedSymbol(JsonNode response, String instrument) {
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode symbol : response.path("symbol")) {
            if (instrument.equalsIgnoreCase(symbol.path("symbolName").asText())) {
                matches.add(symbol);
            }
        }
        if (matches.isEmpty()) throw new CTraderAuthenticationException("CTRADER_UNKNOWN_INSTRUMENT");
        if (matches.size() > 1) throw new CTraderAuthenticationException("CTRADER_AMBIGUOUS_INSTRUMENT");
        return matches.getFirst();
    }

    private JsonNode findAuthorizedAccount(JsonNode response, String externalAccountId) {
        for (JsonNode account : response.path("ctidTraderAccount")) {
            if (externalAccountId.equals(account.path("ctidTraderAccountId").asText())) return account;
        }
        throw new CTraderAuthenticationException("cTrader account is not authorized");
    }

    private String assetName(JsonNode assets, String assetId) {
        if (assetId == null) return null;
        for (JsonNode asset : assets.path("asset")) {
            if (assetId.equals(asset.path("assetId").asText())) return text(asset, "name");
        }
        return null;
    }

    private String firstText(JsonNode primary, JsonNode fallback, String field) {
        String value = text(primary, field);
        return value != null ? value : text(fallback, field);
    }

    private Long firstLong(JsonNode primary, JsonNode fallback, String field) {
        Long value = longValue(primary, field);
        return value != null ? value : longValue(fallback, field);
    }

    private Integer integerValue(JsonNode primary, String field, JsonNode fallback) {
        Integer value = integerValue(primary, field);
        return value != null ? value : integerValue(fallback, field);
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.path(field).isValueNode()
                ? node.path(field).asText() : null;
    }

    private Boolean booleanValue(JsonNode node, String field) {
        return node != null && node.has(field) && node.path(field).isBoolean() ? node.path(field).asBoolean() : null;
    }

    private Long longValue(JsonNode node, String field) {
        return node != null && node.has(field) && node.path(field).canConvertToLong() ? node.path(field).asLong() : null;
    }

    private Integer integerValue(JsonNode node, String field) {
        return node != null && node.has(field) && node.path(field).canConvertToInt() ? node.path(field).asInt() : null;
    }

    private BigDecimal money(JsonNode node, String field, Integer digits) {
        return node != null && digits != null && node.has(field) && node.path(field).isNumber()
                ? node.path(field).decimalValue().movePointLeft(Math.max(0, digits)) : null;
    }

    record ResolvedSymbol(long symbolId, JsonNode listed, JsonNode details) {
    }

    private static Long optionalPositiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(0);
        return value > 0 ? value : null;
    }

    private static Long optionalNonNegativeLong(JsonNode node, String field) {
        if (!node.has(field) || !node.path(field).canConvertToLong()) return null;
        long value = node.path(field).asLong();
        return value >= 0 ? value : null;
    }

    private int orderType(String value) {
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "MARKET" -> 1;
            default -> throw new CTraderAuthenticationException("CTRADER_UNSUPPORTED_ORDER_TYPE");
        };
    }

    private int tradeSide(String value) {
        if ("BUY".equalsIgnoreCase(value)) return 1;
        if ("SELL".equalsIgnoreCase(value)) return 2;
        throw new CTraderAuthenticationException("cTrader trade side is unsupported");
    }

    @Override
    public CTraderReadOnlyObservation readOnlyObservation(CTraderToken token, String externalAccountId,
                                                           java.time.Instant from, java.time.Instant to) {
        Instant started = Instant.now();
        try (Session session = new Session(externalAccountId)) {
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

    @Override
    public CTraderExecutionInstrumentContract readExecutionInstrumentContract(CTraderToken token,
                                                                                String externalAccountId,
                                                                                String instrument) {
        try (Session session = new Session(externalAccountId)) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(externalAccountId);
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);
            return resolveSymbol(session, accountId, instrument);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader instrument metadata read failed", exception);
        }
    }

    @Override
    public CTraderInstrumentMetadata readInstrumentMetadata(CTraderToken token,
                                                             String externalAccountId,
                                                             String instrument) {
        try (Session session = new Session(externalAccountId)) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(externalAccountId);
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);

            JsonNode account = findAuthorizedAccount(session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("accessToken", token.accessToken())), externalAccountId);
            JsonNode traderResponse = requireAccountResponse(session.request(TRADER_REQUEST, TRADER_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            JsonNode assets = requireAccountResponse(session.request(ASSET_LIST_REQUEST, ASSET_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)), accountId);
            ResolvedSymbol resolved = resolveSymbolNodes(session, accountId, instrument);
            return mapInstrumentMetadata(externalAccountId, account, traderResponse, assets, resolved);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader economic metadata read failed", exception);
        }
    }

    CTraderInstrumentMetadata mapInstrumentMetadata(String externalAccountId, JsonNode account,
                                                    JsonNode traderResponse, JsonNode assets,
                                                    ResolvedSymbol resolved) {
        JsonNode trader = traderResponse.path("trader");
        Integer moneyDigits = integerValue(trader, "moneyDigits");
        return new CTraderInstrumentMetadata(
                    new CTraderInstrumentMetadata.CTraderAccountFacts(
                            externalAccountId,
                            booleanValue(account, "isLive"),
                            text(trader, "accountType"),
                            assetName(assets, text(trader, "depositAssetId")),
                            money(trader, "balance", moneyDigits),
                            money(trader, "equity", moneyDigits),
                            money(trader, "usedMargin", moneyDigits),
                            money(trader, "freeMargin", moneyDigits),
                            longValue(trader, "leverageInCents"),
                            longValue(trader, "maxLeverageInCents"),
                            integerValue(trader, "moneyDigits"),
                            text(trader, "marginCalculationType")),
                    new CTraderInstrumentMetadata.CTraderSymbolFacts(
                            resolved.symbolId(),
                            text(resolved.listed(), "symbolName"),
                            firstText(resolved.details(), resolved.listed(), "description"),
                            firstLong(resolved.details(), resolved.listed(), "symbolCategoryId"),
                            assetName(assets, firstText(resolved.details(), resolved.listed(), "baseAssetId")),
                            assetName(assets, firstText(resolved.details(), resolved.listed(), "quoteAssetId")),
                            integerValue(resolved.details(), "digits", resolved.listed()),
                            integerValue(resolved.details(), "pipPosition", resolved.listed()),
                            longValue(resolved.details(), "lotSize"),
                            longValue(resolved.details(), "minVolume"),
                            longValue(resolved.details(), "maxVolume"),
                            longValue(resolved.details(), "stepVolume"),
                            longValue(resolved.details(), "commission"),
                            text(resolved.details(), "commissionType"),
                            longValue(resolved.details(), "minCommission"),
                            text(resolved.details(), "minCommissionType"),
                            longValue(resolved.details(), "swapLong"),
                            longValue(resolved.details(), "swapShort"),
                            text(resolved.details(), "swapCalculationType"),
                            integerValue(resolved.details(), "tripleSwapDay"),
                            text(resolved.details(), "tradingMode"),
                            text(resolved.details(), "measurementUnits"),
                            longValue(resolved.details(), "slDistance"),
                            longValue(resolved.details(), "tpDistance"),
                            text(resolved.details(), "distanceSetIn")));
    }

    @Override
    public CTraderQuote readCurrentQuote(CTraderToken token, String externalAccountId, String instrument) {
        try (Session session = new Session(externalAccountId)) {
            validateEnvironment();
            authenticateApplication(session);
            long accountId = Long.parseLong(externalAccountId);
            requireAccountResponse(session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode().put("ctidTraderAccountId", accountId)
                            .put("accessToken", token.accessToken())), accountId);
            CTraderExecutionInstrumentContract symbol = resolveSymbol(session, accountId, instrument);
            ObjectNode subscribe = objectMapper.createObjectNode()
                    .put("ctidTraderAccountId", accountId)
                    .put("subscribeToSpotTimestamp", true);
            subscribe.putArray("symbolId").add(symbol.symbolId());
            requireAccountResponse(session.request(SUBSCRIBE_SPOTS_REQUEST, SUBSCRIBE_SPOTS_RESPONSE, subscribe), accountId);
            try {
                JsonNode spot = session.awaitSpot(accountId, symbol.symbolId());
                BigDecimal bid = CTraderPriceDecoder.decode(spot.path("bid").longValue(), symbol.digits());
                BigDecimal ask = CTraderPriceDecoder.decode(spot.path("ask").longValue(), symbol.digits());
                Instant observedAt = Instant.ofEpochMilli(spot.path("timestamp").longValue());
                return new CTraderQuote(symbol.symbolName(), bid, ask, observedAt);
            } finally {
                ObjectNode unsubscribe = objectMapper.createObjectNode()
                        .put("ctidTraderAccountId", accountId);
                unsubscribe.putArray("symbolId").add(symbol.symbolId());
                session.request(UNSUBSCRIBE_SPOTS_REQUEST, UNSUBSCRIBE_SPOTS_RESPONSE, unsubscribe);
            }
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader quote read failed", exception);
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
        log.info("ctrader_observation_response_fields accountId={} trader={} assets={} symbols={} reconcile={} pnl={} deals={}",
                externalAccountId, fields(trader), fields(assets), fields(symbols), fields(reconcile), fields(pnl), fields(deals));
        log.info("ctrader_observation_response_shape accountId={} trader={} assets={} symbols={} reconcile={} pnl={} deals={}",
                externalAccountId,
                responseShape(trader, "trader"),
                responseShape(assets, "asset"),
                responseShape(symbols, "symbol"),
                responseShape(reconcile, "position", "order"),
                responseShape(pnl, "positionUnrealizedPnl"),
                responseShape(deals, "deal", "hasMore"));
        log.info("ctrader_observation_response_nested_shape accountId={} trader={} position={} order={} pnl={} deal={}",
                externalAccountId,
                responseShape(trader.path("trader"), "balance", "equity", "usedMargin", "freeMargin",
                        "depositAssetId", "moneyDigits", "accountType", "totalMarginCalculationType",
                        "leverageInCents", "managerBonus", "ibBonus", "nonWithdrawableBonus"),
                firstItemFields(reconcile, "position"),
                firstItemFields(reconcile, "order"),
                firstItemFields(pnl, "positionUnrealizedPnl"),
                firstItemFields(deals, "deal"));
        List<String> reasons = new java.util.ArrayList<>();
        requireObject(trader, "trader", reasons);
        requireArray(assets, "asset", reasons);
        requireArray(symbols, "symbol", reasons);
        allowOmittedArray(reconcile, "position", reasons);
        allowOmittedArray(reconcile, "order", reasons);
        allowOmittedArray(pnl, "positionUnrealizedPnl", reasons);
        allowOmittedArray(deals, "deal", reasons);

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
        CTraderReadOnlyObservation.CollectionState<JsonNode> rawPositions = collectionState(reconcile, "position");
        CTraderReadOnlyObservation.CollectionState<JsonNode> rawOrders = collectionState(reconcile, "order");
        BigDecimal usedMargin = money(traderNode.path("usedMargin"), moneyDigits);
        BigDecimal freeMargin = money(traderNode.path("freeMargin"), moneyDigits);
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
            }
            BigDecimal stopLoss = position.has("stopLoss") && position.path("stopLoss").isNumber()
                    ? position.path("stopLoss").decimalValue() : null;
            if (position.has("stopLoss") && stopLoss != null && stopLoss.signum() <= 0) {
                if (stopLoss.signum() < 0) {
                    reasons.add("POSITION_STOP_LOSS_INVALID:" + position.path("positionId").asText());
                }
                stopLoss = null;
            }
            int positionMoneyDigits = position.path("moneyDigits").asInt(moneyDigits);
            positions.add(new CTraderReadOnlyObservation.Position(instrument, quantity,
                    entryPrice, observedAt, position.path("positionId").asText(), positionMargin, stopLoss,
                    providerText(tradeData, "symbolId"), providerText(tradeData, "tradeSide"), rawVolume,
                    providerField(position, "moneyDigits", JsonNode::asInt),
                    providerMoneyField(position, "swap", positionMoneyDigits),
                    providerMoneyField(position, "commission", positionMoneyDigits),
                    providerMoneyField(position, "mirroringCommission", positionMoneyDigits)));
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
            JsonNode closeDetail = deal.path("closePositionDetail");
            boolean closingPosition = closeDetail.isObject();
            String positionReference = deal.has("positionId") ? deal.path("positionId").asText(null) : null;
            if (positionReference == null || positionReference.isBlank()) {
                reasons.add("DEAL_POSITION_REFERENCE_UNAVAILABLE:" + deal.path("dealId").asText());
            }
            dealFacts.add(new CTraderReadOnlyObservation.Deal(deal.path("dealId").asText(), positionReference,
                    instrument, rawFilledVolume.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64),
                    deal.path("tradeSide").asText("UNKNOWN"), executionPrice, money(deal.path("commission"), dealDigits),
                    money(closeDetail.path("swap"), dealDigits), closePnl(closeDetail, dealDigits),
                    instant(deal.path("executionTimestamp"), started), closingPosition));
        }

        CTraderReadOnlyObservation.CollectionState<JsonNode> rawPnl = collectionState(pnl, "positionUnrealizedPnl");
        List<CTraderReadOnlyObservation.PositionPnl> pnlFacts = new java.util.ArrayList<>();
        boolean pnlComplete = rawPnl.availability() != CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT;
        if (rawPositions.availability() == CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_NON_EMPTY
                && positions.size() != rawPositions.values().size()) {
            pnlComplete = false;
        }
        java.util.Set<String> positionReferences = new java.util.HashSet<>();
        for (CTraderReadOnlyObservation.Position position : positions) {
            positionReferences.add(position.providerReference());
        }
        java.util.Set<String> pnlReferences = new java.util.HashSet<>();
        for (JsonNode item : pnl.path("positionUnrealizedPnl")) {
            String positionReference = providerText(item, "positionId");
            if (positionReference == null || !pnlReferences.add(positionReference)) pnlComplete = false;
            int pnlMoneyDigits = pnl.path("moneyDigits").asInt(moneyDigits);
            var gross = providerMoneyField(item, "grossUnrealizedPnl", pnlMoneyDigits);
            var net = providerMoneyField(item, "netUnrealizedPnl", pnlMoneyDigits);
            if (positionReference == null || !net.present()) pnlComplete = false;
            pnlFacts.add(new CTraderReadOnlyObservation.PositionPnl(positionReference == null ? "UNKNOWN" : positionReference,
                    gross, net, providerField(pnl, "moneyDigits", JsonNode::asInt)));
        }
        if (!positionReferences.equals(pnlReferences)) pnlComplete = false;
        // Equity is an account-authoritative provider fact. Never reconstruct it
        // from local balance plus an unrealized-PnL observation.
        BigDecimal equity = money(traderNode.path("equity"), moneyDigits);
        if (!pnlComplete) reasons.add("UNREALIZED_PNL_UNAVAILABLE");
        if (equity == null) reasons.add("EQUITY_UNAVAILABLE");
        if (usedMargin == null) reasons.add("USED_MARGIN_UNAVAILABLE");
        if (freeMargin == null) reasons.add("FREE_MARGIN_UNAVAILABLE");
        Instant providerObservedAt = latestProviderTimestamp(trader, reconcile, deals, started, reasons);
        if (rawPositions.availability() == CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:position");
        }
        if (rawOrders.availability() == CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:order");
        }
        if (rawPnl.availability() == CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:positionUnrealizedPnl");
        }
        boolean complete = reasons.isEmpty() && pnlComplete && !deals.path("hasMore").asBoolean(false)
                && valuationAsset != null && !valuationAsset.isBlank();
        if (deals.path("hasMore").asBoolean(false)) reasons.add("DEAL_HISTORY_TRUNCATED");
        Instant completed = Instant.now();
        var accountFacts = new CTraderReadOnlyObservation.AccountFacts(
                providerEnumField(traderNode, "accountType", this::accountType),
                providerEnumField(traderNode, "totalMarginCalculationType", this::marginCalculationType),
                providerField(traderNode, "depositAssetId", JsonNode::asText),
                providerField(traderNode, "moneyDigits", JsonNode::asInt),
                providerField(traderNode, "leverageInCents", JsonNode::asLong),
                providerMoneyField(traderNode, "managerBonus", moneyDigits),
                providerMoneyField(traderNode, "ibBonus", moneyDigits),
                providerMoneyField(traderNode, "nonWithdrawableBonus", moneyDigits));
        var positionSet = mapCollection(rawPositions, positions);
        var orderSet = mapCollection(rawOrders, orders);
        var pnlSet = mapCollection(rawPnl, pnlFacts);
        return new CTraderReadOnlyObservation(externalAccountId, valuationAsset == null ? "UNKNOWN" : valuationAsset,
                balance, equity, usedMargin, freeMargin, positions, orders, dealFacts,
                started, providerObservedAt, completed, complete, List.copyOf(new LinkedHashSet<>(reasons)),
                accountFacts, positionSet, orderSet, pnlSet, pnlComplete);
    }

    private <T> CTraderReadOnlyObservation.CollectionState<T> mapCollection(
            CTraderReadOnlyObservation.CollectionState<JsonNode> raw, List<T> values) {
        return new CTraderReadOnlyObservation.CollectionState<>(raw.availability(), values);
    }

    private CTraderReadOnlyObservation.CollectionState<JsonNode> collectionState(JsonNode response, String field) {
        // cTrader's official JSON representation may omit zero-entry repeated
        // fields. The caller reaches this mapper only after a correlated,
        // account-validated successful response has been decoded.
        if (!response.has(field)) {
            return CTraderReadOnlyObservation.CollectionState.available(List.of());
        }
        if (!response.path(field).isArray()) {
            return CTraderReadOnlyObservation.CollectionState.unavailable();
        }
        List<JsonNode> values = new ArrayList<>();
        response.path(field).forEach(values::add);
        return CTraderReadOnlyObservation.CollectionState.available(values);
    }

    private String providerText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }

    private <T> CTraderReadOnlyObservation.ProviderField<T> providerField(
            JsonNode node, String field, java.util.function.Function<JsonNode, T> decoder) {
        return node.hasNonNull(field)
                ? CTraderReadOnlyObservation.ProviderField.present(decoder.apply(node.path(field)))
                : CTraderReadOnlyObservation.ProviderField.absent();
    }

    private CTraderReadOnlyObservation.ProviderField<String> providerEnumField(
            JsonNode node, String field, java.util.function.Function<JsonNode, String> decoder) {
        return providerField(node, field, decoder);
    }

    private String accountType(JsonNode value) {
        if (value.isIntegralNumber()) {
            return switch (value.intValue()) {
                case 0 -> "HEDGED";
                case 1 -> "NETTED";
                case 2 -> "SPREAD_BETTING";
                default -> "UNKNOWN(" + value.intValue() + ")";
            };
        }
        return value.asText();
    }

    private String marginCalculationType(JsonNode value) {
        if (value.isIntegralNumber()) {
            return switch (value.intValue()) {
                case 0 -> "MAX";
                case 1 -> "SUM";
                case 2 -> "NET";
                default -> "UNKNOWN(" + value.intValue() + ")";
            };
        }
        return value.asText();
    }

    private CTraderReadOnlyObservation.ProviderField<BigDecimal> providerMoneyField(
            JsonNode node, String field, int moneyDigits) {
        return node.hasNonNull(field)
                ? CTraderReadOnlyObservation.ProviderField.present(money(node.path(field), moneyDigits))
                : CTraderReadOnlyObservation.ProviderField.absent();
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

    private void allowOmittedArray(JsonNode response, String field, List<String> reasons) {
        if (response.has(field) && !response.path(field).isArray()) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:" + field);
        }
    }

    private void requireObject(JsonNode response, String field, List<String> reasons) {
        if (!response.has(field) || !response.path(field).isObject()) {
            reasons.add("RESPONSE_FIELD_UNAVAILABLE:" + field);
        }
    }

    private List<String> fields(JsonNode response) {
        List<String> fields = new ArrayList<>();
        response.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    /** Logs presence and cardinality only; provider values and payload contents remain redacted. */
    private String responseShape(JsonNode response, String... targetFields) {
        List<String> shape = new ArrayList<>();
        for (String field : targetFields) {
            if (!response.has(field)) {
                shape.add(field + "=ABSENT");
                continue;
            }
            JsonNode value = response.path(field);
            if (value.isNull()) {
                shape.add(field + "=NULL");
            } else if (value.isArray()) {
                shape.add(field + "=" + (value.isEmpty() ? "EMPTY" : "NON_EMPTY")
                        + ":count=" + value.size());
            } else {
                shape.add(field + "=PRESENT:type=" + value.getNodeType());
            }
        }
        return String.join(",", shape);
    }

    private String firstItemFields(JsonNode response, String field) {
        if (!response.has(field)) return field + "=ABSENT";
        JsonNode values = response.path(field);
        if (!values.isArray()) return field + "=NOT_ARRAY";
        if (values.isEmpty()) return field + "=EMPTY";
        JsonNode first = values.get(0);
        return field + "=" + (first.isObject() ? fields(first) : first.getNodeType());
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
        // cTrader omits event collections, and therefore their timestamps, when
        // an account has no positions, orders, or deals. The successful response
        // identity and balance facts still establish the bounded observation.
        return latest == null ? fallback : latest;
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
        private final String externalAccountId;

        private Session(String externalAccountId) {
            this.externalAccountId = externalAccountId;
            safety.reserveRequest(externalAccountId, 0);
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
            safety.reserveRequest(externalAccountId, requestType);
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
                    boolean correlated = response.path("clientMsgId").asText().equals(clientMsgId);
                    int actualPayloadType = response.path("payloadType").asInt();
                    if (correlated && actualPayloadType == responseType) {
                        return response.path("payload");
                    }
                    if (actualPayloadType == ACCOUNT_DISCONNECT_EVENT
                            && (correlated || JsonWebSocketCTraderOpenApiClient.this
                            .accountDisconnectMatchesSession(externalAccountId, response))) {
                        throw JsonWebSocketCTraderOpenApiClient.this.providerFailure(
                                requestType, response, correlated ? clientMsgId : null);
                    }
                    if (correlated && (response.path("errorCode").isTextual()
                            || response.path("payload").path("errorCode").isTextual())) {
                        throw JsonWebSocketCTraderOpenApiClient.this.providerFailure(requestType, response, clientMsgId);
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

        private JsonNode awaitSpot(long accountId, long symbolId) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try {
                while (System.nanoTime() < deadline) {
                    String raw = messages.poll(1, TimeUnit.SECONDS);
                    if (raw == null) continue;
                    JsonNode response = objectMapper.readTree(raw);
                    JsonNode payload = response.path("payload");
                    if (response.path("payloadType").asInt() == SPOT_EVENT
                            && payload.path("ctidTraderAccountId").asLong() == accountId
                            && payload.path("symbolId").asLong() == symbolId) {
                        if (payload.has("bid") && payload.has("ask") && payload.has("timestamp")) {
                            return payload;
                        }
                        continue;
                    }
                    if (response.path("errorCode").isTextual()
                            || payload.path("errorCode").isTextual()) {
                        throw new CTraderAuthenticationException("cTrader Open API rejected the request");
                    }
                }
                throw new CTraderAuthenticationException("cTrader quote facts are incomplete");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CTraderAuthenticationException("cTrader quote request interrupted", exception);
            } catch (CTraderAuthenticationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CTraderAuthenticationException("cTrader quote response is invalid", exception);
            }
        }

        @Override
        public void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    CTraderAuthenticationException providerFailure(int requestType, JsonNode response,
                                                   String clientMsgId) {
        JsonNode payload = response.path("payload");
        int responseType = response.path("payloadType").asInt();
        String errorCode = safeDiagnosticText(firstDiagnosticText(response, payload, "errorCode"));
        String reason = safeDiagnosticText(firstDiagnosticText(payload, response, "reason"));
        String description = safeDiagnosticText(firstDiagnosticText(response, payload, "description"));
        String message = responseType == ACCOUNT_DISCONNECT_EVENT
                ? "cTrader account disconnected during " + requestMessage(requestType)
                : "cTrader Open API rejected " + requestMessage(requestType);
        return new CTraderAuthenticationException(message,
                new CTraderAuthenticationException.ProviderDiagnostic(
                        requestStage(requestType), requestType, requestMessage(requestType),
                        responseType, responseMessage(responseType), blankToNull(errorCode),
                        blankToNull(reason), blankToNull(description), blankToNull(clientMsgId)));
    }

    private String firstDiagnosticText(JsonNode first, JsonNode second, String field) {
        String value = first.path(field).asText("");
        return value.isBlank() ? second.path(field).asText("") : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    boolean accountDisconnectMatchesSession(String externalAccountId, JsonNode response) {
        if (externalAccountId == null || externalAccountId.isBlank()) return false;
        return externalAccountId.equals(response.path("payload").path("ctidTraderAccountId").asText());
    }

    private String safeDiagnosticText(String value) {
        if (value == null || value.isBlank()) return null;
        String redacted = value.replaceAll(
                "(?i)(access[_ -]?token|refresh[_ -]?token|client[_ -]?secret|authorization)"
                        + "\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+", "$1=[REDACTED]");
        redacted = redacted.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        return redacted.length() <= 256 ? redacted : redacted.substring(0, 256);
    }

    private String requestStage(int payloadType) {
        return switch (payloadType) {
            case APPLICATION_AUTH_REQUEST -> "APP_AUTH";
            case ACCOUNT_LIST_REQUEST -> "ACCOUNT_LIST";
            case ACCOUNT_AUTH_REQUEST -> "ACCOUNT_AUTH";
            default -> "CTRADER_REQUEST";
        };
    }

    private String requestMessage(int payloadType) {
        return switch (payloadType) {
            case APPLICATION_AUTH_REQUEST -> "ProtoOAApplicationAuthReq";
            case ACCOUNT_LIST_REQUEST -> "ProtoOAGetAccountListByAccessTokenReq";
            case ACCOUNT_AUTH_REQUEST -> "ProtoOAAccountAuthReq";
            default -> "payload type " + payloadType;
        };
    }

    private String responseMessage(int payloadType) {
        return switch (payloadType) {
            case APPLICATION_AUTH_RESPONSE -> "ProtoOAApplicationAuthRes";
            case ACCOUNT_LIST_RESPONSE -> "ProtoOAGetAccountListByAccessTokenRes";
            case ACCOUNT_AUTH_RESPONSE -> "ProtoOAAccountAuthRes";
            case ACCOUNT_DISCONNECT_EVENT -> "ProtoOAAccountDisconnectEvent";
            default -> "payload type " + payloadType;
        };
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
