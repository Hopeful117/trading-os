package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.util.*;

public record EntryStrategy(EntryType type, BigDecimal price, Set<String> conditions) {
    public EntryStrategy {
        Objects.requireNonNull(type);
        if (type != EntryType.MARKET && (price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("Limit and stop entries require a positive price");
        }
        if (price != null && price.signum() <= 0) {
            throw new IllegalArgumentException("Entry price must be positive");
        }
        conditions = Set.copyOf(conditions);
    }
}
