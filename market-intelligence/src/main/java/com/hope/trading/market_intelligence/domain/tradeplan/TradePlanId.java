package com.hope.trading.market_intelligence.domain.tradeplan;

import java.util.*;

public record TradePlanId(UUID value) {
    public TradePlanId { Objects.requireNonNull(value); }
}
