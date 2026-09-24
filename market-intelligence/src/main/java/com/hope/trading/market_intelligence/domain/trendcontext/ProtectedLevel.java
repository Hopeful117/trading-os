package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;

public record ProtectedLevel(SwingType type, BigDecimal price, ConfirmedSwing source) { }
