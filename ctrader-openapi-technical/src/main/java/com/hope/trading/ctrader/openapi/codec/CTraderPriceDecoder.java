package com.hope.trading.ctrader.openapi.codec;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CTraderPriceDecoder {
    private CTraderPriceDecoder() { }

    public static BigDecimal decode(long relativePrice, int digits) {
        if (digits < 0 || digits > 18) throw new IllegalArgumentException("digits must be between 0 and 18");
        return BigDecimal.valueOf(relativePrice).movePointLeft(5).setScale(digits, RoundingMode.HALF_UP);
    }
}
