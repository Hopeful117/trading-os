package com.hope.trading.market_intelligence.domain.orderbook;

import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.feature.FeatureDataType;
import com.hope.trading.market_intelligence.domain.feature.FeatureDefinition;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroup;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroupStatus;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingnessPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureSchema;
import com.hope.trading.market_intelligence.domain.feature.FeatureUnit;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;
import com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class OrderBookLiquidityCalculator {
    public static final String VERSION = "order-book-liquidity-v1";
    private static final Duration MAX_INPUT_AGE = Duration.ofMinutes(15);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);
    private static final String VOLUME_IMBALANCE = "liquidity.volume_imbalance";
    private static final String MICROPRICE = "liquidity.microprice";
    private static final FeatureSchema SCHEMA = schema();

    public OrderBookLiquidityFeatureSet calculate(OrderBookContext context) {
        FeatureMissingness groupStatus = status(context);
        List<FeatureValue> values;
        if (context.status() == OrderBookContext.ReplayStatus.AVAILABLE && isFresh(context)) {
            values = calculateValues(context);
            groupStatus = values.stream().allMatch(value -> value.status() == FeatureMissingness.PRESENT)
                    ? FeatureMissingness.PRESENT : firstNonPresent(values);
        } else if (context.status() == OrderBookContext.ReplayStatus.AVAILABLE) {
            groupStatus = FeatureMissingness.STALE_INPUT;
            values = unavailableValues(groupStatus);
        } else {
            values = unavailableValues(groupStatus);
        }
        String parameterFingerprint = ArtifactFingerprint.ofInputs(List.of(
                VERSION, String.valueOf(context.depth()), "visible-depth=all-replayed-levels")).value();
        String resultFingerprint = ArtifactFingerprint.ofInputs(List.of(
                VERSION, context.marketId().toString(), context.sequenceId() == null ? "null" : context.sequenceId().toString(),
                context.evaluationCutoff().toString(), String.valueOf(context.sourceEvidenceFingerprint()),
                canonicalValues(values))).value();
        return new OrderBookLiquidityFeatureSet(
                VERSION, context.marketId(), context.depth(),
                context.sequenceId() == null ? java.util.UUID.nameUUIDFromBytes(
                        (context.marketId() + ":unavailable").getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        : context.sequenceId(),
                context.evaluationCutoff(),
                context.latestOccurredAt() == null ? context.evaluationCutoff() : context.latestOccurredAt(),
                context.latestKnownAt() == null ? context.evaluationCutoff() : context.latestKnownAt(),
                context.sourceStartOrdinal(), context.sourceEndOrdinal(),
                context.normalizationVersion() == null ? "unavailable" : context.normalizationVersion(),
                context.sourceEvidenceFingerprint() == null ? "unavailable" : context.sourceEvidenceFingerprint(),
                SCHEMA, values,
                List.of(new FeatureGroupStatus(FeatureGroup.LIQUIDITY, groupStatus,
                        context.reason() == null ? List.of() : List.of(context.reason()))),
                VERSION, parameterFingerprint, resultFingerprint);
    }

    private boolean isFresh(OrderBookContext context) {
        return !context.latestKnownAt().isBefore(context.evaluationCutoff().minus(MAX_INPUT_AGE));
    }

    private String canonicalValues(List<FeatureValue> values) {
        return values.stream().map(value -> {
            if (value instanceof NumericFeatureValue(String name, FeatureMissingness status,
                    BigDecimal numericValue)) {
                String canonicalValue = numericValue == null
                        ? "null" : numericValue.stripTrailingZeros().toPlainString();
                return name + ":" + status + ":" + canonicalValue;
            }
            return value.name() + ":" + value.status();
        }).toList().toString();
    }

    private List<FeatureValue> calculateValues(OrderBookContext context) {
        if (context.bids().isEmpty() || context.asks().isEmpty()) {
            return unavailableValues(FeatureMissingness.INSUFFICIENT_DATA);
        }
        BigDecimal bid = context.bids().getFirst().price();
        BigDecimal ask = context.asks().getFirst().price();
        if (bid.compareTo(ask) > 0) return unavailableValues(FeatureMissingness.INVALID_INPUT);

        BigDecimal bidQuantity = sum(context.bids());
        BigDecimal askQuantity = sum(context.asks());
        BigDecimal totalQuantity = bidQuantity.add(askQuantity);
        BigDecimal midpoint = bid.add(ask).divide(BigDecimal.valueOf(2));
        BigDecimal spread = ask.subtract(bid);
        BigDecimal relativeSpread = spread.divide(midpoint, 16, RoundingMode.HALF_UP);
        List<FeatureValue> values = new ArrayList<>();
        values.add(value("liquidity.best_bid", bid));
        values.add(value("liquidity.best_ask", ask));
        values.add(value("liquidity.spread_absolute", spread));
        values.add(value("liquidity.spread_relative", relativeSpread));
        values.add(value("liquidity.spread_bps", relativeSpread.multiply(TEN_THOUSAND)
                .setScale(8, RoundingMode.HALF_UP)));
        values.add(value("liquidity.bid_visible_quantity", bidQuantity));
        values.add(value("liquidity.ask_visible_quantity", askQuantity));
        values.add(value("liquidity.total_visible_quantity", totalQuantity));
        if (totalQuantity.signum() == 0) {
            values.add(missing(VOLUME_IMBALANCE, FeatureMissingness.INSUFFICIENT_DATA));
            values.add(missing(MICROPRICE, FeatureMissingness.INSUFFICIENT_DATA));
        } else {
            values.add(value(VOLUME_IMBALANCE, bidQuantity.subtract(askQuantity)
                    .divide(totalQuantity, 16, RoundingMode.HALF_UP)));
            BigDecimal topBidQuantity = context.bids().getFirst().quantity();
            BigDecimal topAskQuantity = context.asks().getFirst().quantity();
            BigDecimal topTotal = topBidQuantity.add(topAskQuantity);
            values.add(topTotal.signum() == 0
                    ? missing(MICROPRICE, FeatureMissingness.INSUFFICIENT_DATA)
                    : value(MICROPRICE, ask.multiply(topBidQuantity)
                    .add(bid.multiply(topAskQuantity)).divide(topTotal, 16, RoundingMode.HALF_UP)));
        }
        return values;
    }

    private List<FeatureValue> unavailableValues(FeatureMissingness status) {
        return SCHEMA.definitions().stream()
                .map(definition -> (FeatureValue) missing(definition.name(), status)).toList();
    }

    private FeatureMissingness status(OrderBookContext context) {
        return switch (context.status()) {
            case AVAILABLE -> FeatureMissingness.PRESENT;
            case INVALID -> FeatureMissingness.INVALID_INPUT;
            case UNAVAILABLE -> FeatureMissingness.UNAVAILABLE;
        };
    }

    private FeatureMissingness firstNonPresent(List<FeatureValue> values) {
        return values.stream().map(FeatureValue::status)
                .filter(value -> value != FeatureMissingness.PRESENT).findFirst()
                .orElse(FeatureMissingness.UNAVAILABLE);
    }

    private BigDecimal sum(List<OrderBookContext.Level> levels) {
        return levels.stream().map(OrderBookContext.Level::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private NumericFeatureValue value(String name, BigDecimal value) {
        return new NumericFeatureValue(name, FeatureMissingness.PRESENT, value);
    }

    private NumericFeatureValue missing(String name, FeatureMissingness status) {
        return new NumericFeatureValue(name, status, null);
    }

    private static FeatureSchema schema() {
        List<FeatureDefinition> definitions = new ArrayList<>();
        add(definitions, 0, "liquidity.best_bid", FeatureUnit.PRICE, "Highest valid bid price");
        add(definitions, 1, "liquidity.best_ask", FeatureUnit.PRICE, "Lowest valid ask price");
        add(definitions, 2, "liquidity.spread_absolute", FeatureUnit.PRICE, "Best ask minus best bid");
        add(definitions, 3, "liquidity.spread_relative", FeatureUnit.DECIMAL_FRACTION, "Absolute spread divided by midpoint");
        add(definitions, 4, "liquidity.spread_bps", FeatureUnit.BASIS_POINTS, "Relative spread multiplied by 10000");
        add(definitions, 5, "liquidity.bid_visible_quantity", FeatureUnit.QUANTITY, "Sum of replayed bid quantities");
        add(definitions, 6, "liquidity.ask_visible_quantity", FeatureUnit.QUANTITY, "Sum of replayed ask quantities");
        add(definitions, 7, "liquidity.total_visible_quantity", FeatureUnit.QUANTITY, "Sum of replayed bid and ask quantities");
        add(definitions, 8, VOLUME_IMBALANCE, FeatureUnit.DECIMAL_FRACTION, "Bid/ask quantity imbalance");
        add(definitions, 9, MICROPRICE, FeatureUnit.PRICE, "Top-level quantity-weighted microprice");
        return new FeatureSchema(VERSION, definitions);
    }

    private static void add(List<FeatureDefinition> definitions, int order, String name,
                            FeatureUnit unit, String description) {
        definitions.add(new FeatureDefinition(name, FeatureGroup.LIQUIDITY, FeatureDataType.NUMERIC,
                unit, "order-book-liquidity", name, order,
                FeatureMissingnessPolicy.NULL_WHEN_UNAVAILABLE, description));
    }
}
