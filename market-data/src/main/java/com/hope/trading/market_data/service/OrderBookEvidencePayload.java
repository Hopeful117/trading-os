package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookLevel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class OrderBookEvidencePayload {
    private OrderBookEvidencePayload() {
    }

    static String encode(List<OrderBookLevel> levels) {
        return levels.stream()
                .map(level -> level.price().toPlainString() + ":" + level.quantity().toPlainString())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    static List<OrderBookLevel> decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<OrderBookLevel> levels = new ArrayList<>();
        for (String value : payload.split(";", -1)) {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid order-book evidence payload");
            }
            levels.add(new OrderBookLevel(new BigDecimal(parts[0]), new BigDecimal(parts[1])));
        }
        return List.copyOf(levels);
    }
}
