package com.hope.trading.broker_service.ctrader.e2e;

import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenClient;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Synthetic OAuth token exchange used only by the isolated E2E runtime.
 * It never contacts cTrader and accepts only the documented test code.
 */
@Component
@Primary
@Profile("e2e-controlled-broker")
public final class ControlledCTraderTokenClient implements CTraderTokenClient {
    @Override
    public CTraderToken exchangeAuthorizationCode(String code, String redirectUri) {
        if (!"e2e-authorization-code".equals(code)) {
            throw new IllegalArgumentException("controlled broker authorization code rejected");
        }
        return new CTraderToken("e2e-access-token", "e2e-refresh-token", Instant.now().plusSeconds(3600));
    }
}
