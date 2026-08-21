package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal applicability semantics of a strategy version. Expresses WHERE and
 * WHEN the setup may apply; selection logic lives in later stories.
 */
public record StrategyApplicability(
        Set<String> assetClasses,
        Set<Timeframe> timeframes,
        Set<String> providers
) {

    public StrategyApplicability {
        assetClasses = normalized(assetClasses);
        timeframes = timeframes == null ? Set.of() : Set.copyOf(timeframes);
        providers = normalized(providers);
        if (assetClasses.isEmpty()) {
            throw new IllegalArgumentException("at least one asset class is required");
        }
        if (timeframes.isEmpty()) {
            throw new IllegalArgumentException("at least one timeframe is required");
        }
    }

    private static Set<String> normalized(Set<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum Timeframe {
        M1,
        M5,
        M15,
        M30,
        H1,
        H4,
        D1,
        W1;

        public static Timeframe parse(String raw) {
            Objects.requireNonNull(raw, "timeframe is required");
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace("MIN", "M");
            return switch (normalized) {
                case "15M" -> M15;
                case "1M" -> M1;
                case "5M" -> M5;
                case "30M" -> M30;
                case "1H", "60M" -> H1;
                case "4H", "240M" -> H4;
                case "1D", "D" -> D1;
                case "1W", "W" -> W1;
                default -> valueOf(normalized);
            };
        }
    }
}
