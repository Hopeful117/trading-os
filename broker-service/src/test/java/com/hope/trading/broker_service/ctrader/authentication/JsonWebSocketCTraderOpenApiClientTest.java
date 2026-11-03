package com.hope.trading.broker_service.ctrader.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JsonWebSocketCTraderOpenApiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonWebSocketCTraderOpenApiClient client = new JsonWebSocketCTraderOpenApiClient(
            new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token",
                    "wss://demo.ctraderapi.com:5035", 60),
            new CTraderRequestSafetyGuard(mock(JdbcTemplate.class),
                    new CTraderSafetyProperties(false, 1000, 700, 800,
                            Duration.ofMinutes(20), Duration.ofMinutes(30)), Clock.systemUTC()), mapper);

    @Test
    void directOrderSubmissionIsBlockedBeforeOpeningASocket() {
        var request = new CTraderOpenApiClient.CTraderOrderRequest(
                "49289512", "EUR/USD", "BUY", "LIMIT", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, "client-order");

        assertThatThrownBy(() -> client.submitOrder(null, request))
                .isInstanceOf(CTraderSafetyException.class)
                .hasMessage("cTrader execution is disabled by safety policy");
    }

    @Test
    void expectedMarginUsesDocumentedPayloadTypesAndMatchesResponseVolume() throws Exception {
        Method requestMessage = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("requestMessage", int.class);
        Method responseMessage = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("responseMessage", int.class);
        requestMessage.setAccessible(true);
        responseMessage.setAccessible(true);

        assertThat(invoke(requestMessage, client, 2139)).isEqualTo("ProtoOAExpectedMarginReq");
        assertThat(invoke(responseMessage, client, 2140)).isEqualTo("ProtoOAExpectedMarginRes");
        assertThatThrownBy(() -> client.validateExpectedMarginResponse(
                mapper.readTree("{\"margin\":[{\"volume\":200,\"buyMargin\":100}] }"), 100L))
                .hasMessage("CTRADER_EXPECTED_MARGIN_UNSUPPORTED_RESPONSE");
        client.validateExpectedMarginResponse(
                mapper.readTree("{\"margin\":[{\"volume\":100,\"buyMargin\":100}] }"), 100L);
    }

    @Test
    void validatesReadOnlyQuoteFactsWithExactDecimalPrices() {
        var quote = new CTraderOpenApiClient.CTraderQuote(
                "EUR/USD", new BigDecimal("1.12345"), new BigDecimal("1.12346"),
                Instant.parse("2026-10-30T12:00:00Z"));

        assertThat(quote.bid()).isEqualByComparingTo("1.12345");
        assertThat(quote.ask()).isEqualByComparingTo("1.12346");
        assertThat(quote.observedAt()).isEqualTo(Instant.parse("2026-10-30T12:00:00Z"));
    }

    @Test
    void rejectsInvalidReadOnlyQuoteFacts() {
        assertThatThrownBy(() -> new CTraderOpenApiClient.CTraderQuote(
                "EUR/USD", BigDecimal.ZERO, BigDecimal.ONE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quote bid must be positive");
        assertThatThrownBy(() -> new CTraderOpenApiClient.CTraderQuote(
                "EUR/USD", BigDecimal.ONE, new BigDecimal("0.99"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quote ask must not be below bid");
        assertThatThrownBy(() -> new CTraderOpenApiClient.CTraderQuote(
                "EUR/USD", BigDecimal.ONE, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quote timestamp is required");
    }

    @Test
    void rejectsAccountScopedResponseForAnotherAccount() throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("requireAccountResponse", JsonNode.class, long.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> invoke(method, client, mapper.readTree("{\"ctidTraderAccountId\":9002}"), 9001L))
                .hasCauseInstanceOf(CTraderAuthenticationException.class)
                .hasRootCauseMessage("cTrader response account identity mismatch");
    }

    @Test
    void preservesAccountDisconnectDetailsWithoutSecrets() throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("providerFailure", int.class, JsonNode.class, String.class);
        method.setAccessible(true);

        CTraderAuthenticationException exception = (CTraderAuthenticationException) invoke(method, client,
                2102, mapper.readTree("""
                        {"clientMsgId":"corr-1","payloadType":2142,
                         "payload":{"ctidTraderAccountId":49289512,
                         "reason":"Access Token is expired"}}
                        """), "corr-1");

        assertThat(exception.getMessage()).contains("ProtoOAAccountAuthReq");
        assertThat(exception.getMessage()).doesNotContain("access-token-secret");
        assertThat(exception.diagnostic()).satisfies(diagnostic -> {
            assertThat(diagnostic.stage()).isEqualTo("ACCOUNT_AUTH");
            assertThat(diagnostic.requestPayloadType()).isEqualTo(2102);
            assertThat(diagnostic.requestMessage()).isEqualTo("ProtoOAAccountAuthReq");
            assertThat(diagnostic.responsePayloadType()).isEqualTo(2142);
            assertThat(diagnostic.responseMessage()).isEqualTo("ProtoOAAccountDisconnectEvent");
            assertThat(diagnostic.providerReason()).isEqualTo("Access Token is expired");
            assertThat(diagnostic.correlationId()).isEqualTo("corr-1");
        });
    }

    @Test
    void recognizesUncorrelatedAccountDisconnectForTheBoundSession() throws Exception {
        JsonNode response = mapper.readTree("""
                {"payloadType":2142,"payload":{"ctidTraderAccountId":49289512,
                 "reason":"account disabled"}}
                """);

        assertThat(client.accountDisconnectMatchesSession("49289512", response)).isTrue();
        assertThat(client.accountDisconnectMatchesSession("9001", response)).isFalse();
    }

    @Test
    void preservesGenericProviderErrorDetailsWithoutPayloadSecrets() throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("providerFailure", int.class, JsonNode.class, String.class);
        method.setAccessible(true);

        CTraderAuthenticationException exception = (CTraderAuthenticationException) invoke(method, client,
                2102, mapper.readTree("""
                        {"clientMsgId":"corr-2","payloadType":2103,
                         "payload":{"errorCode":"CH_ACCESS_TOKEN_INVALID",
                         "description":"accessToken=secret refresh_token=other\\n token rejected"}}
                        """), "corr-2");

        assertThat(exception.diagnostic().providerErrorCode()).isEqualTo("CH_ACCESS_TOKEN_INVALID");
        assertThat(exception.diagnostic().providerDescription())
                .isEqualTo("accessToken=[REDACTED] refresh_token=[REDACTED] token rejected");
        assertThat(exception.diagnostic().providerDescription()).doesNotContain("secret", "other");
        assertThat(exception.getMessage()).doesNotContain("token rejected");
    }

    @Test
    void mapsPrecisionAndVolumeWithoutInferringMissingFacts() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2,"accountType":0,"totalMarginCalculationType":0,"leverageInCents":10000,"managerBonus":0,"ibBonus":125,"nonWithdrawableBonus":50}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("""
                {"ctidTraderAccountId":9001,"symbol":[{"symbolId":7,"symbolName":"EUR/USD"}]}
                """);
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"usedMargin":500,"moneyDigits":2,"stopLoss":1.05,"utcLastUpdateTimestamp":1792756800000,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[{"orderId":99,"orderStatus":"ACCEPTED","executedVolume":100,"utcLastUpdateTimestamp":1792756900000,"tradeData":{"symbolId":7,"volume":500,"clientOrderId":"client-1"}}]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[{"positionId":10,"netUnrealizedPnl":1000}]}
                """);
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[{"dealId":88,"positionId":10,"symbolId":7,"moneyDigits":2,"executionPrice":1.2,"filledVolume":300,"tradeSide":"BUY","commission":10,"utcLastUpdateTimestamp":1792756950000,"executionTimestamp":1792756950000,"closePositionDetail":{"grossProfit":20,"swap":2}}],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isTrue();
        assertThat(observation.balance()).isEqualByComparingTo("1000");
        assertThat(observation.equity()).isEqualByComparingTo("1010");
        assertThat(observation.usedMargin()).isEqualByComparingTo("100");
        assertThat(observation.freeMargin()).isEqualByComparingTo("900");
        assertThat(observation.positions()).singleElement().satisfies(position -> {
            assertThat(position.instrument()).isEqualTo("EUR/USD");
            assertThat(position.signedQuantity()).isEqualByComparingTo("2");
            assertThat(position.margin()).isEqualByComparingTo("5");
            assertThat(position.stopLoss()).isEqualByComparingTo("1.05");
        });
        assertThat(observation.orders()).singleElement().satisfies(order -> {
            assertThat(order.providerReference()).isEqualTo("99");
            assertThat(order.clientReference()).isEqualTo("client-1");
            assertThat(order.quantity()).isEqualByComparingTo("5");
            assertThat(order.filledQuantity()).isEqualByComparingTo("1");
        });
            assertThat(observation.deals()).singleElement().satisfies(deal -> {
                assertThat(deal.providerReference()).isEqualTo("88");
                assertThat(deal.providerPositionReference()).isEqualTo("10");
                assertThat(deal.closingPosition()).isTrue();
            assertThat(deal.quantity()).isEqualByComparingTo("3");
            assertThat(deal.price()).isEqualByComparingTo("1.2");
            assertThat(deal.fee()).isEqualByComparingTo("0.1");
            assertThat(deal.swap()).isEqualByComparingTo("0.02");
            assertThat(deal.realizedPnl()).isEqualByComparingTo("0.2");
        });
        assertThat(observation.startedAt()).isEqualTo(started);
        assertThat(observation.accountFacts().accountType().value()).isEqualTo("HEDGED");
        assertThat(observation.accountFacts().totalMarginCalculationType().value()).isEqualTo("MAX");
        assertThat(observation.accountFacts().managerBonus().value()).isEqualByComparingTo("0");
        assertThat(observation.accountFacts().ibBonus().value()).isEqualByComparingTo("1.25");
        assertThat(observation.accountFacts().nonWithdrawableBonus().value()).isEqualByComparingTo("0.5");
        assertThat(observation.positionSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_NON_EMPTY);
        assertThat(observation.orderSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_NON_EMPTY);
        assertThat(observation.pnlSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_NON_EMPTY);
        assertThat(observation.pnlComplete()).isTrue();
    }

    @Test
    void treatsZeroStopLossAsExplicitlyUnprotected() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("""
                {"ctidTraderAccountId":9001,"symbol":[{"symbolId":7,"symbolName":"EUR/USD"}]}
                """);
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"usedMargin":500,"moneyDigits":2,"stopLoss":0,"utcLastUpdateTimestamp":1792756800000,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[{"positionId":10,"netUnrealizedPnl":100}]}
                """);
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isTrue();
        assertThat(observation.reasons()).doesNotContain("POSITION_STOP_LOSS_INVALID:10");
        assertThat(observation.positions()).singleElement()
                .satisfies(position -> assertThat(position.stopLoss()).isNull());
    }

    @Test
    void preservesExplicitlyEmptyCollectionsAsAvailableEmptyState() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[]}");
        JsonNode reconcile = mapper.readTree("{\"ctidTraderAccountId\":9001,\"position\":[],\"order\":[]}");
        JsonNode pnl = mapper.readTree("{\"ctidTraderAccountId\":9001,\"moneyDigits\":2,\"positionUnrealizedPnl\":[]}");
        JsonNode deals = mapper.readTree("{\"ctidTraderAccountId\":9001,\"hasMore\":false}");

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isFalse();
        assertThat(observation.positions()).isEmpty();
        assertThat(observation.orders()).isEmpty();
        assertThat(observation.deals()).isEmpty();
        assertThat(observation.providerObservedAt()).isEqualTo(started);
        assertThat(observation.positionSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.orderSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.pnlSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.pnlComplete()).isTrue();
        assertThat(observation.dealSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT);
    }

    @Test
    void normalizesOmittedRepeatedCollectionsAsAuthoritativeEmptyWhenResponseIsValid() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("""
                {"ctidTraderAccountId":9001,"symbol":[]}
                """);
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"order":[]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[]}
                """);
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isTrue();
        assertThat(observation.positions()).isEmpty();
        assertThat(observation.positionSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.orderSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.pnlSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.pnlComplete()).isTrue();
        assertThat(observation.reasons()).isEmpty();
    }

    @Test
    void normalizesOmittedPnlCollectionAsAuthoritativeEmptyWhenResponseIsValid() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("{\"ctidTraderAccountId\":9001,\"asset\":[{\"assetId\":1,\"name\":\"USD\"}]}");
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[]}");
        JsonNode reconcile = mapper.readTree("{\"ctidTraderAccountId\":9001,\"position\":[],\"order\":[]}");
        JsonNode pnl = mapper.readTree("{\"ctidTraderAccountId\":9001,\"moneyDigits\":2}");
        JsonNode deals = mapper.readTree("{\"ctidTraderAccountId\":9001,\"deal\":[],\"hasMore\":false}");

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.pnlSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.AVAILABLE_EMPTY);
        assertThat(observation.pnlComplete()).isTrue();
        assertThat(observation.complete()).isTrue();
    }

    @Test
    void keepsMalformedRepeatedCollectionUnavailable() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("{\"ctidTraderAccountId\":9001,\"asset\":[{\"assetId\":1,\"name\":\"USD\"}]}");
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[]}");
        JsonNode reconcile = mapper.readTree("{\"ctidTraderAccountId\":9001,\"position\":{},\"order\":[]}");
        JsonNode pnl = mapper.readTree("{\"ctidTraderAccountId\":9001,\"moneyDigits\":2,\"positionUnrealizedPnl\":[]}");
        JsonNode deals = mapper.readTree("{\"ctidTraderAccountId\":9001,\"deal\":[],\"hasMore\":false}");

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.positionSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT);
        assertThat(observation.complete()).isFalse();
        assertThat(observation.reasons()).contains("RESPONSE_FIELD_UNAVAILABLE:position");
    }

    @Test
    void classifiesRawRepeatedFieldsWithoutLoggingTheirValues() throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class
                .getDeclaredMethod("responseShape", JsonNode.class, String[].class);
        method.setAccessible(true);

        JsonNode response = mapper.readTree("""
                {"position":[],"order":[{"positionId":7}],"moneyDigits":2}
                """);

        String shape = (String) invoke(method, client, response, new String[]{"position", "order", "deal", "moneyDigits"});

        assertThat(shape).isEqualTo("position=EMPTY:count=0,order=NON_EMPTY:count=1,deal=ABSENT,moneyDigits=PRESENT:type=NUMBER");
    }

    @Test
    void duplicatePnlFactsAreIncomplete() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("{\"ctidTraderAccountId\":9001,\"asset\":[{\"assetId\":1,\"name\":\"USD\"}]}");
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[{\"symbolId\":7,\"symbolName\":\"EUR/USD\"}]}");
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"usedMargin":500,"moneyDigits":2,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[{"positionId":10,"netUnrealizedPnl":100},{"positionId":10,"netUnrealizedPnl":101}]}
                """);
        JsonNode deals = mapper.readTree("{\"ctidTraderAccountId\":9001,\"deal\":[],\"hasMore\":false}");

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.pnlComplete()).isFalse();
        assertThat(observation.complete()).isFalse();
    }

    @Test
    void preservesPositionButMarksObservationIncompleteWhenMarginIsMissing() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("""
                {"ctidTraderAccountId":9001,"symbol":[{"symbolId":7,"symbolName":"EUR/USD"}]}
                """);
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"moneyDigits":2,"stopLoss":0,"utcLastUpdateTimestamp":1792756800000,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[]}
                """);
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isFalse();
        assertThat(observation.reasons()).contains("POSITION_MARGIN_UNAVAILABLE:10");
        assertThat(observation.positions()).singleElement()
                .satisfies(position -> assertThat(position.margin()).isNull());
    }

    @Test
    void rejectsIncompleteDealFacts() {
        assertThatThrownBy(() -> new CTraderReadOnlyObservation.Deal(
                "88", "EUR/USD", null, "BUY", BigDecimal.ONE, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deal facts are required");
    }

    @Test
    void rejectsMissingObservationTimestamps() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CTraderReadOnlyObservation(
                "9001", "USD", BigDecimal.ONE, null, null, null,
                List.of(), List.of(), List.of(), null, now, now, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("observation timestamps are required");
    }

    @Test
    void mapsProviderEconomicFactsAndLeavesMissingFactsNull() throws Exception {
        JsonNode account = mapper.readTree("""
                {"ctidTraderAccountId":9001,"isLive":false}
                """);
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"accountType":"HEDGED","depositAssetId":1,
                "balance":5000000,"moneyDigits":2,"leverageInCents":10000}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"},
                {"assetId":2,"name":"EUR"}]}
        """);
        var resolved = new JsonWebSocketCTraderOpenApiClient.ResolvedSymbol(1,
                mapper.readTree("{\"symbolName\":\"EURUSD\"}"),
                mapper.readTree("""
                {"description":"Euro vs US Dollar","symbolCategoryId":7,"baseAssetId":2,
                "quoteAssetId":1,"digits":5,"pipPosition":4,"lotSize":10000000,
                "minVolume":100000,"maxVolume":10000000000,"stepVolume":100000,
                "swapLong":-3,"swapShort":-1,"tripleSwapDay":5,"slDistance":0,
                "tpDistance":0,"distanceSetIn":"POINTS"}
                """));

        CTraderInstrumentMetadata metadata = client.mapInstrumentMetadata("9001", account, trader, assets, resolved);

        assertThat(metadata.account().providerLive()).isFalse();
        assertThat(metadata.account().accountCurrency()).isEqualTo("USD");
        assertThat(metadata.account().balance()).isEqualByComparingTo("50000");
        assertThat(metadata.symbol().baseAsset()).isEqualTo("EUR");
        assertThat(metadata.symbol().quoteAsset()).isEqualTo("USD");
        assertThat(metadata.symbol().swapLong()).isEqualTo(-3L);
        assertThat(metadata.symbol().commission()).isNull();
        assertThat(metadata.symbol().distanceSetIn()).isEqualTo("POINTS");
    }

    @Test
    void resolvesOnlyOneExactProviderSymbol() throws Exception {
        assertThat(client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"}]}"), "eurusd")
                .path("symbolId").asLong()).isEqualTo(1);
        assertThatThrownBy(() -> client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"}]}"), "EUR/USD"))
                .isInstanceOf(CTraderAuthenticationException.class)
                .hasMessage("CTRADER_UNKNOWN_INSTRUMENT");
        assertThatThrownBy(() -> client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"},{\"symbolId\":2,\"symbolName\":\"EURUSD\"}]}"), "EURUSD"))
                .isInstanceOf(CTraderAuthenticationException.class)
                .hasMessage("CTRADER_AMBIGUOUS_INSTRUMENT");
    }

    private CTraderReadOnlyObservation invokeMapper(JsonNode trader, JsonNode assets, JsonNode symbols,
                                                     JsonNode reconcile, JsonNode pnl, JsonNode deals,
                                                     Instant started) throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod("mapObservation",
                String.class, JsonNode.class, JsonNode.class, JsonNode.class, JsonNode.class,
                JsonNode.class, JsonNode.class, Instant.class);
        method.setAccessible(true);
        return (CTraderReadOnlyObservation) invoke(method, client, "9001", trader, assets, symbols, reconcile, pnl, deals, started);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw new Exception(exception.getCause());
        }
    }
}
