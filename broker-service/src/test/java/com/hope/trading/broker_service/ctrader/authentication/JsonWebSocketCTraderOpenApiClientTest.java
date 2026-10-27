package com.hope.trading.broker_service.ctrader.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonWebSocketCTraderOpenApiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonWebSocketCTraderOpenApiClient client = new JsonWebSocketCTraderOpenApiClient(
            new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token",
                    "wss://demo.ctraderapi.com:5035", 60), mapper);

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
    void mapsPrecisionAndVolumeWithoutInferringMissingFacts() throws Exception {
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
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"usedMargin":500,"moneyDigits":2,"stopLoss":1.05,"utcLastUpdateTimestamp":1792756800000,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[{"orderId":99,"orderStatus":"ACCEPTED","executedVolume":100,"utcLastUpdateTimestamp":1792756900000,"tradeData":{"symbolId":7,"volume":500,"clientOrderId":"client-1"}}]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[{"positionId":10,"netUnrealizedPnl":1000}]}
                """);
        JsonNode deals = mapper.readTree("""
                {"ctidTraderAccountId":9001,"deal":[{"dealId":88,"symbolId":7,"moneyDigits":2,"executionPrice":1.2,"filledVolume":300,"tradeSide":"BUY","commission":10,"utcLastUpdateTimestamp":1792756950000,"executionTimestamp":1792756950000,"closePositionDetail":{"grossProfit":20,"swap":2}}],"hasMore":false}
                """);

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isTrue();
        assertThat(observation.balance()).isEqualByComparingTo("1000");
        assertThat(observation.equity()).isEqualByComparingTo("1010");
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
            assertThat(deal.quantity()).isEqualByComparingTo("3");
            assertThat(deal.price()).isEqualByComparingTo("1.2");
            assertThat(deal.fee()).isEqualByComparingTo("0.1");
            assertThat(deal.swap()).isEqualByComparingTo("0.02");
            assertThat(deal.realizedPnl()).isEqualByComparingTo("0.2");
        });
        assertThat(observation.startedAt()).isEqualTo(started);
    }

    @Test
    void treatsZeroStopLossAsExplicitlyUnprotected() throws Exception {
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
                {"ctidTraderAccountId":9001,"position":[{"positionId":10,"price":1.1,"usedMargin":500,"moneyDigits":2,"stopLoss":0,"utcLastUpdateTimestamp":1792756800000,"tradeData":{"symbolId":7,"volume":200,"tradeSide":"BUY"}}],"order":[]}
                """);
        JsonNode pnl = mapper.readTree("""
                {"ctidTraderAccountId":9001,"moneyDigits":2,"positionUnrealizedPnl":[]}
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
    void treatsOmittedEmptyCollectionsAsEmptyAccountState() throws Exception {
        Instant started = Instant.parse("2026-10-23T12:00:00Z");
        JsonNode trader = mapper.readTree("""
                {"ctidTraderAccountId":9001,"trader":{"balance":100000,"depositAssetId":1,"moneyDigits":2}}
                """);
        JsonNode assets = mapper.readTree("""
                {"ctidTraderAccountId":9001,"asset":[{"assetId":1,"name":"USD"}]}
                """);
        JsonNode symbols = mapper.readTree("{\"ctidTraderAccountId\":9001,\"symbol\":[]}");
        JsonNode reconcile = mapper.readTree("{\"ctidTraderAccountId\":9001}");
        JsonNode pnl = mapper.readTree("{\"ctidTraderAccountId\":9001,\"moneyDigits\":2}");
        JsonNode deals = mapper.readTree("{\"ctidTraderAccountId\":9001,\"hasMore\":false}");

        var observation = invokeMapper(trader, assets, symbols, reconcile, pnl, deals, started);

        assertThat(observation.complete()).isTrue();
        assertThat(observation.positions()).isEmpty();
        assertThat(observation.orders()).isEmpty();
        assertThat(observation.deals()).isEmpty();
        assertThat(observation.providerObservedAt()).isEqualTo(started);
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
