package com.hope.trading.broker_service.ctrader.authentication;

import java.util.List;
import java.time.Instant;
import java.math.BigDecimal;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;

public interface CTraderOpenApiClient {
    List<String> discoverAuthorizedAccountIds(CTraderToken token);

    void authorizeAccount(CTraderToken token, String externalAccountId);

    default boolean isDemoAccount(CTraderToken token, String externalAccountId) {
        throw new CTraderAuthenticationException("cTrader account environment is unavailable");
    }

    default CTraderOrderResponse submitOrder(CTraderToken token, CTraderOrderRequest request) {
        throw new CTraderAuthenticationException("cTrader order execution is unavailable");
    }

    default CTraderClosePositionResponse closePosition(CTraderToken token, CTraderClosePositionRequest request) {
        throw new CTraderAuthenticationException("cTrader position management is unavailable");
    }

    record CTraderOrderRequest(String externalAccountId, String instrument, String side,
                                String orderType, BigDecimal quantity, BigDecimal limitPrice,
                                BigDecimal stopLossPrice, List<BigDecimal> takeProfitPrices,
                                BigDecimal referencePrice, String clientOrderId) {
        public CTraderOrderRequest(String externalAccountId, String instrument, String side,
                                   String orderType, BigDecimal quantity, BigDecimal limitPrice,
                                   BigDecimal stopLossPrice, String clientOrderId) {
            this(externalAccountId, instrument, side, orderType, quantity, limitPrice,
                    stopLossPrice, List.of(), null, clientOrderId);
        }

        public CTraderOrderRequest(String externalAccountId, String instrument, String side,
                                   String orderType, BigDecimal quantity, BigDecimal limitPrice,
                                   BigDecimal stopLossPrice, List<BigDecimal> takeProfitPrices,
                                   String clientOrderId) {
            this(externalAccountId, instrument, side, orderType, quantity, limitPrice,
                    stopLossPrice, takeProfitPrices, null, clientOrderId);
        }
    }

    record CTraderOrderResponse(String externalOrderId) {
    }

    record CTraderClosePositionRequest(String externalAccountId, long positionId, String instrument,
                                       BigDecimal quantity, String clientRequestId) {
    }

    record CTraderClosePositionResponse(String externalOrderId, String correlationId) {
    }

    default CTraderReadOnlyObservation readOnlyObservation(CTraderToken token,
                                                            String externalAccountId,
                                                            Instant from,
                                                            Instant to) {
        throw new CTraderAuthenticationException("cTrader read-only synchronization is unavailable");
    }
}
