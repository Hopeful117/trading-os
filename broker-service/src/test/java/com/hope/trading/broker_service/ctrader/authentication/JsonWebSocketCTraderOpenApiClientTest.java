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
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

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
    void exhaustedBudgetRefusesBeforeWebSocketCreation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).contains("request_count = request_count + 1") ? 0 : 1);
        var guard = new CTraderRequestSafetyGuard(jdbc,
                new CTraderSafetyProperties(true, 1, 1, 1,
                        Duration.ofMinutes(20), Duration.ofMinutes(30)), Clock.systemUTC());
        var properties = new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token",
                "not-a-websocket-uri", 60);
        var refusedClient = new JsonWebSocketCTraderOpenApiClient(properties, guard, mapper);

        assertThatThrownBy(() -> refusedClient.discoverAuthorizedAccountIds(
                new CTraderToken("access", "refresh", Instant.now().plusSeconds(3600))))
                .isInstanceOf(CTraderSafetyException.class)
                .hasMessage("cTrader daily request safety stop reached");
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
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"equity":101000,"usedMargin":10000,"freeMargin":90000,"depositAssetId":1,"moneyDigits":2,"accountType":0,"totalMarginCalculationType":0,"leverageInCents":10000,"managerBonus":0,"ibBonus":125,"nonWithdrawableBonus":50,"balanceVersion":42}}
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
        assertThat(observation.providerObservedAt()).isEqualTo(started);
        assertThat(observation.accountFacts().accountType().value()).isEqualTo("HEDGED");
        assertThat(observation.accountFacts().totalMarginCalculationType().value()).isEqualTo("MAX");
        assertThat(observation.accountFacts().managerBonus().value()).isEqualByComparingTo("0");
        assertThat(observation.accountFacts().ibBonus().value()).isEqualByComparingTo("1.25");
        assertThat(observation.accountFacts().nonWithdrawableBonus().value()).isEqualByComparingTo("0.5");
        assertThat(observation.accountFacts().balanceVersion().value()).isEqualTo(42L);
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
    void recordsUnavailableFactsInsteadOfInventingMalformedProviderData() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree(
                "{\"ctidTraderAccountId\":9001,\"trader\":{\"balance\":100000,\"depositAssetId\":1,\"moneyDigits\":2}}");
        JsonNode assets = mapper.readTree("{\"ctidTraderAccountId\":9001,\"asset\":[{\"assetId\":1,\"name\":\"USD\"}]}");
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[{\"symbolId\":7,\"symbolName\":\"EUR/USD\"}]}");
        JsonNode reconcile = mapper.readTree("""
                {"ctidTraderAccountId":9001,"position":[
                  {"positionId":1,"price":1.1,"tradeData":{"symbolId":99,"volume":100,"tradeSide":"BUY"}},
                  {"positionId":2,"price":1.1,"tradeData":{"symbolId":7,"tradeSide":"BUY"}},
                  {"positionId":3,"price":0,"tradeData":{"symbolId":7,"volume":100,"tradeSide":"BUY"}}
                ],"order":[{"orderId":4,"tradeData":{}}]}
                """);
        JsonNode pnl = mapper.readTree("{\"ctidTraderAccountId\":9001,\"moneyDigits\":2,\"positionUnrealizedPnl\":[]}");
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[
                  {"dealId":1,"symbolId":99,"executionPrice":1.1,"filledVolume":100},
                  {"dealId":2,"symbolId":7,"executionPrice":0,"filledVolume":100},
                  {"dealId":3,"symbolId":7,"executionPrice":1.1}
                ],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isFalse();
        assertThat(observation.reasons()).contains(
                "SYMBOL_MAPPING_UNAVAILABLE:99",
                "POSITION_VOLUME_UNAVAILABLE:2",
                "POSITION_ENTRY_PRICE_UNAVAILABLE:3",
                "ORDER_FACTS_UNAVAILABLE:4",
                "DEAL_SYMBOL_MAPPING_UNAVAILABLE:99",
                "DEAL_EXECUTION_PRICE_UNAVAILABLE:2",
                "DEAL_FACTS_UNAVAILABLE:3");
    }

    @Test
    void coversProviderFactFallbackAndScalarDecodingRules() throws Exception {
        JsonNode primary = mapper.readTree("{\"name\":\"primary\",\"count\":2,\"enabled\":true}");
        JsonNode fallback = mapper.readTree("{\"name\":\"fallback\",\"count\":3}");
        JsonNode assets = mapper.readTree("{\"asset\":[{\"assetId\":1,\"name\":\"USD\"}]}");
        JsonNode accounts = mapper.readTree("{\"ctidTraderAccount\":[{\"ctidTraderAccountId\":\"9001\"}]}");

        assertThat(invokeWithTypes("findAuthorizedAccount", new Class<?>[]{JsonNode.class, String.class}, accounts, "9001"))
                .isEqualTo(accounts.path("ctidTraderAccount").get(0));
        assertThatThrownBy(() -> invokeWithTypes("findAuthorizedAccount",
                new Class<?>[]{JsonNode.class, String.class}, accounts, "missing"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
        assertThat(invokeWithTypes("assetName", new Class<?>[]{JsonNode.class, String.class}, assets, "1"))
                .isEqualTo("USD");
        assertThat(invokeWithTypes("assetName", new Class<?>[]{JsonNode.class, String.class}, assets, "2"))
                .isNull();
        assertThat(invokeWithTypes("firstText", new Class<?>[]{JsonNode.class, JsonNode.class, String.class},
                mapper.readTree("{}"), fallback, "name")).isEqualTo("fallback");
        assertThat(invokeWithTypes("firstLong", new Class<?>[]{JsonNode.class, JsonNode.class, String.class},
                primary, fallback, "count")).isEqualTo(2L);
        assertThat(invokeWithTypes("integerValue", new Class<?>[]{JsonNode.class, String.class, JsonNode.class},
                mapper.readTree("{}"), "count", fallback)).isEqualTo(3);
        assertThat(invokeWithTypes("booleanValue", new Class<?>[]{JsonNode.class, String.class}, primary, "enabled"))
                .isEqualTo(true);
        assertThat(invokeWithTypes("longValue", new Class<?>[]{JsonNode.class, String.class}, primary, "count"))
                .isEqualTo(2L);
        assertThat((BigDecimal) invokeWithTypes("money", new Class<?>[]{JsonNode.class, String.class, Integer.class},
                primary, "count", 2)).isEqualByComparingTo("0.02");
        assertThat(invokeWithTypes("instant", new Class<?>[]{JsonNode.class, Instant.class},
                mapper.readTree("0"), Instant.EPOCH)).isEqualTo(Instant.EPOCH);
        assertThat(invokeWithTypes("normalizeSymbol", new Class<?>[]{String.class}, " eur/usd ")).isEqualTo("EURUSD");
        assertThat(invokeWithTypes("normalizeSymbol", new Class<?>[]{String.class}, (Object) null)).isEqualTo("");
        assertThat(invokeWithTypes("integerValue", new Class<?>[]{JsonNode.class, String.class}, primary, "count"))
                .isEqualTo(2);
        assertThat(invokeWithTypes("integerValue", new Class<?>[]{JsonNode.class, String.class},
                mapper.readTree("{\"count\":\"bad\"}"), "count"))
                .isNull();
        assertThat(invokeWithTypes("optionalPositiveLong", new Class<?>[]{JsonNode.class, String.class}, primary, "count"))
                .isEqualTo(2L);
        assertThat(invokeWithTypes("optionalPositiveLong", new Class<?>[]{JsonNode.class, String.class},
                mapper.readTree("{\"count\":0}"), "count"))
                .isNull();
        assertThat(invokeWithTypes("optionalNonNegativeLong", new Class<?>[]{JsonNode.class, String.class},
                mapper.readTree("{\"count\":0}"), "count"))
                .isEqualTo(0L);
        assertThat(invokeWithTypes("orderType", new Class<?>[]{String.class}, "MARKET")).isEqualTo(1);
        assertThat(invokeWithTypes("tradeSide", new Class<?>[]{String.class}, "BUY")).isEqualTo(1);
        assertThat(invokeWithTypes("tradeSide", new Class<?>[]{String.class}, "SELL")).isEqualTo(2);
        assertThatThrownBy(() -> invokeWithTypes("orderType", new Class<?>[]{String.class}, "LIMIT"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
        assertThatThrownBy(() -> invokeWithTypes("tradeSide", new Class<?>[]{String.class}, "UNKNOWN"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
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
    void resolvesOneExactOrSeparatorNormalizedProviderSymbol() throws Exception {
        assertThat(client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"}]}"), "eurusd")
                .path("symbolId").asLong()).isEqualTo(1);
        assertThat(client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"}]}"), "EUR/USD")
                .path("symbolId").asLong()).isEqualTo(1);
        assertThatThrownBy(() -> client.selectListedSymbol(mapper.readTree(
                "{\"symbol\":[{\"symbolId\":1,\"symbolName\":\"EURUSD\"},{\"symbolId\":2,\"symbolName\":\"EURUSD\"}]}"), "EURUSD"))
                .isInstanceOf(CTraderAuthenticationException.class)
                .hasMessage("CTRADER_AMBIGUOUS_INSTRUMENT");
    }

    @Test
    void coversDeterministicProviderMappingBranches() throws Exception {
        assertThat(invokePrivate("accountType", mapper.readTree("0"))).isEqualTo("HEDGED");
        assertThat(invokePrivate("accountType", mapper.readTree("1"))).isEqualTo("NETTED");
        assertThat(invokePrivate("accountType", mapper.readTree("2"))).isEqualTo("SPREAD_BETTING");
        assertThat(invokePrivate("accountType", mapper.readTree("9"))).isEqualTo("UNKNOWN(9)");
        assertThat(invokePrivate("accountType", mapper.readTree("\"HEDGED\""))).isEqualTo("HEDGED");

        assertThat(invokePrivate("marginCalculationType", mapper.readTree("0"))).isEqualTo("MAX");
        assertThat(invokePrivate("marginCalculationType", mapper.readTree("1"))).isEqualTo("SUM");
        assertThat(invokePrivate("marginCalculationType", mapper.readTree("2"))).isEqualTo("NET");
        assertThat(invokePrivate("marginCalculationType", mapper.readTree("9"))).isEqualTo("UNKNOWN(9)");

        assertThat(invokePrivate("orderType", "MARKET")).isEqualTo(1);
        assertThat(invokePrivate("tradeSide", "BUY")).isEqualTo(1);
        assertThat(invokePrivate("tradeSide", "SELL")).isEqualTo(2);
        assertThatThrownBy(() -> invokePrivate("orderType", "LIMIT"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
        assertThatThrownBy(() -> invokePrivate("tradeSide", "OTHER"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
    }

    @Test
    void classifiesCollectionAndProviderTimestampStates() throws Exception {
        Object available = invokePrivate("collectionState", mapper.readTree("{\"items\":[1]}"), "items");
        Object omitted = invokePrivate("collectionState", mapper.readTree("{}"), "items");
        Object malformed = invokePrivate("collectionState", mapper.readTree("{\"items\":{}}"), "items");
        Object truncated = invokePrivate("dealCollectionState",
                mapper.readTree("{\"deal\":[{\"dealId\":1}],\"hasMore\":true}"));

        assertThat(available.toString()).contains("AVAILABLE_NON_EMPTY");
        assertThat(omitted.toString()).contains("AVAILABLE_EMPTY");
        assertThat(malformed.toString()).contains("UNAVAILABLE_OR_ABSENT");
        assertThat(truncated.toString()).contains("TRUNCATED");

        Instant fallback = Instant.parse("2026-01-01T00:00:00Z");
        Instant latest = (Instant) invokePrivate("latestProviderTimestamp",
                mapper.readTree("{}"), mapper.readTree("{}"), mapper.readTree("{}"), fallback,
                new java.util.ArrayList<String>());
        assertThat(latest).isEqualTo(fallback);
    }

    @Test
    void validatesEnvironmentAndRequiredConfiguration() throws Exception {
        invokePrivate("validateEnvironment");
        assertThatThrownBy(() -> invokePrivate("required", " "))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);

        var mismatched = new JsonWebSocketCTraderOpenApiClient(
                new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token",
                        "wss://live.ctraderapi.com:5035", 60),
                new CTraderRequestSafetyGuard(mock(JdbcTemplate.class),
                        new CTraderSafetyProperties(false, 1000, 700, 800,
                                Duration.ofMinutes(20), Duration.ofMinutes(30)), Clock.systemUTC()), mapper);
        assertThatThrownBy(() -> invokePrivateOn(mismatched, "validateEnvironment"))
                .hasCauseInstanceOf(CTraderAuthenticationException.class);
    }

    private Object invokePrivate(String name, Object... args) throws Exception {
        return invokePrivateOn(client, name, args);
    }

    private static Object invokePrivateOn(Object target, String name, Object... args) throws Exception {
        Method method = switch (name) {
            case "accountType", "marginCalculationType" ->
                    JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name, JsonNode.class);
            case "orderType", "tradeSide", "required" ->
                    JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name, String.class);
            case "validateEnvironment" -> JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name);
            case "collectionState" -> JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name, JsonNode.class, String.class);
            case "dealCollectionState" -> JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name, JsonNode.class);
            case "latestProviderTimestamp" -> JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(
                    name, JsonNode.class, JsonNode.class, JsonNode.class, Instant.class, List.class);
            default -> throw new IllegalArgumentException("unknown method " + name);
        };
        method.setAccessible(true);
        return invoke(method, target, args);
    }

    private Object invokeWithTypes(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = JsonWebSocketCTraderOpenApiClient.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return invoke(method, client, args);
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
