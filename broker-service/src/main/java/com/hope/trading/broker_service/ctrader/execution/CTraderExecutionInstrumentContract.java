package com.hope.trading.broker_service.ctrader.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Provider-owned constraints required to construct a safe cTrader order. */
public record CTraderExecutionInstrumentContract(
        long symbolId,
        String symbolName,
        int digits,
        int pipPosition,
        long minVolume,
        long maxVolume,
        long stepVolume,
        Long lotSize,
        Long stopLossDistance,
        Long takeProfitDistance,
        String distanceSetIn) {

    private static final int PROVIDER_VOLUME_SCALE = 2;
    private static final int PROVIDER_RELATIVE_PRICE_SCALE = 5;

    public CTraderExecutionInstrumentContract {
        if (symbolId <= 0 || symbolName == null || symbolName.isBlank()) {
            throw new IllegalArgumentException("CTRADER_UNKNOWN_INSTRUMENT");
        }
        if (digits < 0 || digits > 18 || pipPosition < 0 || pipPosition > digits) {
            throw new IllegalArgumentException("CTRADER_INVALID_PRICE_PRECISION");
        }
        if (minVolume <= 0 || maxVolume < minVolume || stepVolume <= 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_VOLUME_CONSTRAINTS");
        }
        if (lotSize != null && lotSize <= 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_LOT_SIZE");
        }
        if (stopLossDistance != null && stopLossDistance < 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_STOP_DISTANCE");
        }
        if (takeProfitDistance != null && takeProfitDistance < 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_TAKE_PROFIT_DISTANCE");
        }
    }

    public long providerVolume(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_QUANTITY");
        }
        final long volume;
        try {
            volume = quantity.movePointRight(PROVIDER_VOLUME_SCALE).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("CTRADER_UNREPRESENTABLE_VOLUME", exception);
        }
        if (volume < minVolume) {
            throw new IllegalArgumentException("CTRADER_BELOW_MIN_VOLUME");
        }
        if (volume > maxVolume) {
            throw new IllegalArgumentException("CTRADER_ABOVE_MAX_VOLUME");
        }
        if ((volume - minVolume) % stepVolume != 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_VOLUME_STEP");
        }
        return volume;
    }

    public BigDecimal price(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("CTRADER_INVALID_" + field);
        }
        try {
            return value.setScale(digits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("CTRADER_INVALID_PRICE_PRECISION", exception);
        }
    }

    public long relativeStopLoss(BigDecimal referencePrice, BigDecimal stopLossPrice, String side) {
        return relativeDistance(referencePrice, stopLossPrice, side, true);
    }

    public long relativeTakeProfit(BigDecimal referencePrice, BigDecimal takeProfitPrice, String side) {
        return relativeDistance(referencePrice, takeProfitPrice, side, false);
    }

    private long relativeDistance(BigDecimal referencePrice, BigDecimal protectionPrice,
                                  String side, boolean stopLoss) {
        BigDecimal reference = price(referencePrice, "REFERENCE_PRICE");
        BigDecimal protection = price(protectionPrice, stopLoss ? "STOP_LOSS" : "TAKE_PROFIT");
        boolean buy = "BUY".equalsIgnoreCase(side);
        boolean validDirection = stopLoss
                ? (buy ? protection.compareTo(reference) < 0 : protection.compareTo(reference) > 0)
                : (buy ? protection.compareTo(reference) > 0 : protection.compareTo(reference) < 0);
        if (!validDirection) {
            throw new IllegalArgumentException("CTRADER_INVALID_" + (stopLoss ? "STOP_LOSS" : "TAKE_PROFIT") + "_DIRECTION");
        }
        BigDecimal distance = protection.subtract(reference).abs();
        validateMinimumDistance(distance, reference, stopLoss ? stopLossDistance : takeProfitDistance,
                stopLoss ? "STOP_LOSS" : "TAKE_PROFIT");
        try {
            return distance.movePointRight(PROVIDER_RELATIVE_PRICE_SCALE).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("CTRADER_UNREPRESENTABLE_RELATIVE_DISTANCE", exception);
        }
    }

    private void validateMinimumDistance(BigDecimal distance, BigDecimal referencePrice,
                                         Long minimumDistance, String field) {
        if (minimumDistance == null) return;
        if (distanceSetIn == null) {
            throw new IllegalArgumentException("CTRADER_DISTANCE_UNIT_UNAVAILABLE");
        }
        BigDecimal minimum = switch (distanceSetIn.toUpperCase(java.util.Locale.ROOT)) {
            case "POINTS", "SYMBOL_DISTANCE_IN_POINTS" ->
                    BigDecimal.valueOf(minimumDistance).movePointLeft(digits);
            case "PERCENTAGE", "SYMBOL_DISTANCE_IN_PERCENTAGE" ->
                    referencePrice.multiply(BigDecimal.valueOf(minimumDistance))
                            .movePointLeft(4);
            default -> throw new IllegalArgumentException("CTRADER_DISTANCE_UNIT_UNSUPPORTED");
        };
        if (distance.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("CTRADER_BELOW_MIN_" + field + "_DISTANCE");
        }
    }
}
