package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.time.Instant;

public record StructuralLevel(SwingType type, BigDecimal price, Instant pivotTime,
                              Instant confirmationTime, boolean protectedLevel, ConfirmedSwing swing) { }
