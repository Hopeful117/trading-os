package com.hope.trading.trading_core.risk.infrastructure.client;

import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Fail-closed until a broker-neutral authoritative proposed-order margin read exists. */
@Component
public final class UnavailableRequiredMarginClient implements RequiredMarginPort {
    @Override
    public Optional<Fact> resolve(Request request) {
        return Optional.empty();
    }
}
