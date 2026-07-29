package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record OhlcPoint(
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
}
