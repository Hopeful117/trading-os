package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class TrendContextInputMapper {
    public TrendContextAssessmentInput map(
            Map<TrendContextRole, List<OhlcResponse>> responsesByRole,
            TrendContextProfile profile,
            Instant assessmentAt,
            Instant cutOffAt,
            String ruleVersion
    ) {
        Objects.requireNonNull(responsesByRole, "responsesByRole is required");
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(assessmentAt, "assessmentAt is required");
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        Objects.requireNonNull(ruleVersion, "ruleVersion is required");

        UUID marketId = null;
        String provider = null;
        String symbol = null;
        EnumMap<TrendContextRole, TrendContextRoleSeries> roleSeries =
                new EnumMap<>(TrendContextRole.class);

        for (TrendContextRole role : TrendContextRole.values()) {
            List<OhlcResponse> responses = responsesByRole.get(role);
            TrendContextRoleDefinition definition = profile.roles().get(role);
            if (responses == null || responses.isEmpty()) {
                if (definition != null && definition.required()) {
                    throw new IllegalArgumentException("Missing required role: " + role);
                }
                continue;
            }
            if (definition == null) {
                throw new IllegalArgumentException("Role is not configured: " + role);
            }
            for (OhlcResponse response : responses) {
                if (!definition.interval().equals(normalize(response.interval()))) {
                    throw new IllegalArgumentException("Role interval mismatch: " + role);
                }
                if (marketId == null) marketId = response.marketId();
                if (provider == null) provider = response.provider();
                if (symbol == null) symbol = response.symbol();
                if (!Objects.equals(marketId, response.marketId())
                        || !Objects.equals(provider, response.provider())
                        || !Objects.equals(symbol, response.symbol())) {
                    throw new IllegalArgumentException("Market source identity mismatch");
                }
            }
            RoleMapping mapping = mapRole(
                    role, responses, definition, responseMarketId(responses), assessmentAt, cutOffAt);
            roleSeries.put(role, mapping.series());
        }

        if (marketId == null || provider == null || symbol == null) {
            throw new IllegalArgumentException("No Trend Context market evidence supplied");
        }
        return TrendContextAssessmentInput.accept(
                marketId, provider, symbol, assessmentAt, cutOffAt,
                profile, ruleVersion, roleSeries);
    }

    private RoleMapping mapRole(
            TrendContextRole role,
            List<OhlcResponse> responses,
            TrendContextRoleDefinition definition,
            UUID marketId,
            Instant assessmentAt,
            Instant cutOffAt
    ) {
        List<OhlcResponse> sorted = responses.stream()
                .sorted(Comparator.comparing(OhlcResponse::openTime)
                        .thenComparing(OhlcResponse::closeTime)
                        .thenComparing(value -> required(value.sourceId(), "sourceId")))
                .toList();
        List<OhlcResponse> deduplicated = new ArrayList<>();
        for (OhlcResponse response : sorted) {
            OhlcResponse previous = deduplicated.stream()
                    .filter(value -> value.openTime().equals(response.openTime()))
                    .findFirst().orElse(null);
            if (previous == null) {
                deduplicated.add(response);
            } else if (!sameEvidence(previous, response)) {
                throw new IllegalArgumentException(
                        "Conflicting OHLC duplicate at " + response.openTime());
            }
        }

        List<TrendContextCandle> candles = deduplicated.stream()
                .map(this::toCandle)
                .toList();
        List<String> exclusions = new ArrayList<>();
        List<TrendContextGapFinding> gaps = new ArrayList<>();
        for (TrendContextCandle candle : candles) {
            if (candle.synthetic()) {
                exclusions.add(candle.sourceId() + ":SYNTHETIC_DATA_EXCLUDED");
                gaps.add(new TrendContextGapFinding(
                        role, "SYNTHETIC_DATA_EXCLUDED", candle.openTime(),
                        candle.closeTime(), "Synthetic normalized candle excluded"));
            }
            if (!candle.closed()) {
                exclusions.add(candle.sourceId() + ":OPEN_CANDLE_EXCLUDED");
            }
            if (candle.closeTime().isAfter(cutOffAt)) {
                exclusions.add(candle.sourceId() + ":CUTOFF_EXCLUDED");
            }
        }
        TrendContextSourceReference source = sourceReference(role, definition, marketId, candles);
        List<TrendContextCandle> eligible = candles.stream()
                .filter(value -> value.closed() && !value.synthetic()
                        && !value.closeTime().isAfter(cutOffAt))
                .toList();
        TrendContextFreshness freshness = new TrendContextFreshness(
                definition.intervalDuration(),
                eligible.stream().map(TrendContextCandle::closeTime)
                        .max(Comparator.naturalOrder()).orElse(null),
                candles.stream().map(TrendContextCandle::sourceOccurredAt)
                        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                candles.stream().map(TrendContextCandle::fetchedAt)
                        .max(Comparator.naturalOrder()).orElseThrow(),
                assessmentAt,
                true,
                !eligible.isEmpty());
        return new RoleMapping(TrendContextRoleSeries.of(
                role, definition.interval(), candles, exclusions, gaps,
                source, freshness, cutOffAt));
    }

    private TrendContextCandle toCandle(OhlcResponse response) {
        return new TrendContextCandle(
                required(response.provider(), "provider"),
                required(response.symbol(), "symbol"),
                normalize(response.interval()),
                Objects.requireNonNull(response.openTime(), "openTime is required"),
                Objects.requireNonNull(response.closeTime(), "closeTime is required"),
                response.open(), response.high(), response.low(), response.close(),
                response.volume(), response.closed(), response.synthetic(),
                required(response.sourceId(), "sourceId"), response.occurredAt(),
                Objects.requireNonNull(response.fetchedAt(), "fetchedAt is required"));
    }

    private TrendContextSourceReference sourceReference(
            TrendContextRole role,
            TrendContextRoleDefinition definition,
            UUID marketId,
            List<TrendContextCandle> candles
    ) {
        TrendContextCandle first = candles.getFirst();
        TrendContextCandle last = candles.getLast();
        return new TrendContextSourceReference(
                "market-data", first.provider(), marketId, first.symbol(),
                role, definition.interval(), first.openTime(), last.openTime(),
                candles.stream().map(TrendContextCandle::sourceOccurredAt)
                        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                candles.stream().map(TrendContextCandle::fetchedAt)
                        .max(Comparator.naturalOrder()).orElseThrow(),
                "market-data:" + first.provider() + ":" + first.symbol() + ":" + definition.interval(),
                "");
    }

    private UUID responseMarketId(List<OhlcResponse> responses) {
        return Objects.requireNonNull(responses.getFirst().marketId(), "marketId is required");
    }

    private boolean sameEvidence(OhlcResponse first, OhlcResponse second) {
        return Objects.equals(first.marketId(), second.marketId())
                && Objects.equals(first.provider(), second.provider())
                && Objects.equals(first.symbol(), second.symbol())
                && Objects.equals(first.interval(), second.interval())
                && Objects.equals(first.openTime(), second.openTime())
                && Objects.equals(first.closeTime(), second.closeTime())
                && Objects.equals(first.open(), second.open())
                && Objects.equals(first.high(), second.high())
                && Objects.equals(first.low(), second.low())
                && Objects.equals(first.close(), second.close())
                && Objects.equals(first.volume(), second.volume())
                && Objects.equals(first.vwap(), second.vwap())
                && Objects.equals(first.trades(), second.trades())
                && first.closed() == second.closed()
                && first.synthetic() == second.synthetic()
                && Objects.equals(first.occurredAt(), second.occurredAt());
    }

    private static String normalize(String value) {
        return required(value, "interval").toUpperCase();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private record RoleMapping(TrendContextRoleSeries series) {
    }
}
