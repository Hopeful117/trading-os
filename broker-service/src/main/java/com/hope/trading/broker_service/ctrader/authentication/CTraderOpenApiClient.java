package com.hope.trading.broker_service.ctrader.authentication;

import java.util.List;
import java.time.Instant;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;

public interface CTraderOpenApiClient {
    List<String> discoverAuthorizedAccountIds(CTraderToken token);

    void authorizeAccount(CTraderToken token, String externalAccountId);

    default CTraderReadOnlyObservation readOnlyObservation(CTraderToken token,
                                                            String externalAccountId,
                                                            Instant from,
                                                            Instant to) {
        throw new CTraderAuthenticationException("cTrader read-only synchronization is unavailable");
    }
}
