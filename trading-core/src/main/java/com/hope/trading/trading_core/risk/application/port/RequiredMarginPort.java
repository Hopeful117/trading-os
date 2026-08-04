package com.hope.trading.trading_core.risk.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Read boundary for an authoritative broker/account proposed-order margin fact. */
public interface RequiredMarginPort {
    Optional<Fact> resolve(Request request);

    record Request(UUID brokerAccountId, String instrument, String direction,
                   BigDecimal quantity, BigDecimal price, Instant requestedAt) { }
    record Fact(BigDecimal amount, String currency, String sourceId,
                long sourceVersion, Instant observedAt) { }
}
