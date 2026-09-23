package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TrendContextInputValidation {
    private TrendContextInputValidation() {
    }

    public static List<Finding> validate(
            TrendContextProfile profile,
            Map<TrendContextRole, TrendContextRoleSeries> roleSeries,
            Instant cutOffAt
    ) {
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(roleSeries, "roleSeries is required");
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");

        List<Finding> findings = new ArrayList<>();
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleSeries series = roleSeries.get(role);
            TrendContextRoleDefinition definition = profile.roles().get(role);
            if (series == null) {
                if (definition != null && definition.required()) {
                    findings.add(new Finding("REQUIRED_ROLE_MISSING", role,
                            "Required role is missing"));
                }
                continue;
            }
            if (definition == null || !definition.interval().equals(series.interval())) {
                findings.add(new Finding("ROLE_INTERVAL_MISMATCH", role,
                        "Role series interval does not match the profile"));
            }
            validateCandles(role, series.candles(), cutOffAt, findings);
            if (definition == null) {
                continue;
            }
            if (series.calculationReadyCandles().size()
                    < definition.minimumEligibleCandles()) {
                findings.add(new Finding("INSUFFICIENT_HISTORY", role,
                        "Calculation-ready history is below the profile minimum"));
            }
        }
        return List.copyOf(findings);
    }

    public static void requireAccepted(List<Finding> findings) {
        List<Finding> blocking = findings.stream()
                .filter(item -> !isNonBlockingEvidenceFinding(item.code()))
                .toList();
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid Trend Context input: " + blocking);
        }
    }

    private static boolean isNonBlockingEvidenceFinding(String code) {
        return switch (code) {
            case "INSUFFICIENT_HISTORY", "CUTOFF_EXCLUDED", "OPEN_CANDLE_EXCLUDED",
                    "SYNTHETIC_DATA_EXCLUDED" -> true;
            default -> false;
        };
    }

    private static void validateCandles(
            TrendContextRole role,
            List<TrendContextCandle> candles,
            Instant cutOffAt,
            List<Finding> findings
    ) {
        Map<Instant, TrendContextCandle> byOpenTime = new HashMap<>();
        for (TrendContextCandle candle : candles) {
            if (candle.openTime().compareTo(candle.closeTime()) >= 0
                    || candle.closeTime().isBefore(Instant.EPOCH)) {
                findings.add(new Finding("INVALID_TIMESTAMP", role,
                        "Candle timestamps are impossible"));
            }
            if (!validPrices(candle)) {
                findings.add(new Finding("INVALID_OHLC", role,
                        "Candle OHLC values are invalid"));
            }
            TrendContextCandle previous = byOpenTime.putIfAbsent(candle.openTime(), candle);
            if (previous != null && !sameEvidence(previous, candle)) {
                findings.add(new Finding("DUPLICATE_CONFLICT", role,
                        "Conflicting duplicate candle at " + candle.openTime()));
            }
            if (candle.closeTime().isAfter(cutOffAt)) {
                findings.add(new Finding("CUTOFF_EXCLUDED", role,
                        "Candle close is after the assessment cut-off"));
            }
            if (!candle.closed()) {
                findings.add(new Finding("OPEN_CANDLE_EXCLUDED", role,
                        "Open candle is not calculation-ready"));
            }
            if (candle.synthetic()) {
                findings.add(new Finding("SYNTHETIC_DATA_EXCLUDED", role,
                        "Synthetic candle is not calculation-ready"));
            }
        }
    }

    private static boolean validPrices(TrendContextCandle candle) {
        BigDecimal open = candle.open();
        BigDecimal high = candle.high();
        BigDecimal low = candle.low();
        BigDecimal close = candle.close();
        if (open == null || high == null || low == null || close == null) return false;
        if (open.signum() <= 0 || high.signum() <= 0
                || low.signum() <= 0 || close.signum() <= 0) return false;
        if (high.compareTo(open.max(close)) < 0) return false;
        if (low.compareTo(open.min(close)) > 0) return false;
        if (high.compareTo(low) < 0) return false;
        return candle.volume() == null || candle.volume().signum() >= 0;
    }

    private static boolean sameEvidence(TrendContextCandle first, TrendContextCandle second) {
        return Objects.equals(first.provider(), second.provider())
                && Objects.equals(first.symbol(), second.symbol())
                && Objects.equals(first.interval(), second.interval())
                && Objects.equals(first.openTime(), second.openTime())
                && Objects.equals(first.closeTime(), second.closeTime())
                && Objects.equals(first.open(), second.open())
                && Objects.equals(first.high(), second.high())
                && Objects.equals(first.low(), second.low())
                && Objects.equals(first.close(), second.close())
                && Objects.equals(first.volume(), second.volume())
                && first.closed() == second.closed()
                && first.synthetic() == second.synthetic()
                && Objects.equals(first.sourceOccurredAt(), second.sourceOccurredAt());
    }

    public record Finding(String code, TrendContextRole role, String message) {
        public Finding {
            Objects.requireNonNull(code, "code is required");
            Objects.requireNonNull(role, "role is required");
            Objects.requireNonNull(message, "message is required");
        }
    }
}
