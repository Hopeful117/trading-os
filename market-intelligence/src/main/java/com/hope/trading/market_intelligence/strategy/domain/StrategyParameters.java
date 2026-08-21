package com.hope.trading.market_intelligence.strategy.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable set of uniquely named deterministic strategy parameters.
 */
public record StrategyParameters(List<StrategyParameter> values) {

    public StrategyParameters {
        values = values == null ? List.of() : List.copyOf(values);
        Map<String, Long> duplicates = values.stream().collect(Collectors.groupingBy(
                StrategyParameter::name, Collectors.counting()));
        List<String> duplicated = duplicates.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicated.isEmpty()) {
            throw new IllegalArgumentException("duplicate parameter names: " + duplicated);
        }
    }

    public static StrategyParameters empty() {
        return new StrategyParameters(List.of());
    }

    public Optional<StrategyParameter> find(String name) {
        return values.stream().filter(parameter -> parameter.name().equals(name)).findFirst();
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StrategyParameters parameters && values.equals(parameters.values());
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
