package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A semantic input required by a strategy version, identified by category and
 * stable semantic key. Deliberately decoupled from pipeline implementation
 * types.
 */
public record RequiredSemanticInput(SemanticInputType type, String key) {

    public RequiredSemanticInput {
        Objects.requireNonNull(type, "input type is required");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("semantic input key is required");
        }
        key = key.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return type + ":" + key;
    }
}
