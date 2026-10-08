package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

/** Converts broker margin facts into the reporting currency used by Risk Domain. */
public final class RequiredMarginCurrencyNormalizer {
    private RequiredMarginCurrencyNormalizer() {
    }

    public static Optional<BigDecimal> normalize(RequiredMarginPort.Fact fact,
                                                 String reportingCurrency,
                                                 MarketValuationPort.Snapshot valuation,
                                                 Instant asOf) {
        if (fact == null || fact.amount() == null || fact.amount().signum() <= 0
                || blank(fact.currency()) || blank(reportingCurrency)
                || blank(fact.sourceId()) || fact.sourceVersion() < 1 || fact.observedAt() == null
                || asOf == null || fact.observedAt().isAfter(asOf)
                || valuation == null || !valuation.complete() || valuation.sourceVersion() < 1
                || blank(valuation.reportingCurrency())
                || !normalized(reportingCurrency).equals(normalized(valuation.reportingCurrency()))
                || valuation.valuationTimestamp() == null || valuation.valuationTimestamp().isAfter(asOf)
                || valuation.capturedAt() == null || valuation.capturedAt().isAfter(asOf)
                || stale(valuation, asOf) || staleRelativeToMargin(fact, valuation)
                || valuation.facts() == null) {
            return Optional.empty();
        }
        if (normalized(fact.currency()).equals(normalized(reportingCurrency))) {
            return Optional.of(fact.amount());
        }
        List<MarketValuationPort.Fact> matches = valuation.facts().stream()
                .filter(candidate -> "ASSET".equals(candidate.type())
                        && !blank(candidate.asset())
                        && normalized(fact.currency()).equals(normalized(candidate.asset()))
                        && "AVAILABLE".equals(candidate.status())
                        && candidate.value() != null && candidate.value().signum() > 0)
                .toList();
        return matches.size() == 1
                ? Optional.of(fact.amount().multiply(matches.getFirst().value()))
                : Optional.empty();
    }

    private static boolean stale(MarketValuationPort.Snapshot valuation, Instant asOf) {
        try {
            Duration maxAge = Duration.parse(valuation.maxObservationAge());
            return maxAge.isNegative() || valuation.valuationTimestamp().plus(maxAge).isBefore(asOf);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean staleRelativeToMargin(RequiredMarginPort.Fact fact,
                                                 MarketValuationPort.Snapshot valuation) {
        try {
            Duration maxAge = Duration.parse(valuation.maxObservationAge());
            return valuation.capturedAt().isBefore(fact.observedAt().minus(maxAge));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalized(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
