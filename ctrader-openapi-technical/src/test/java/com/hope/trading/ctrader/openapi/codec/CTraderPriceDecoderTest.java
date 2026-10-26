package com.hope.trading.ctrader.openapi.codec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CTraderPriceDecoderTest {
    @Test
    void decodesProviderScaledPrice() {
        assertEquals(new BigDecimal("123.45678"), CTraderPriceDecoder.decode(12345678, 5));
    }

    @Test
    void rejectsInvalidDigits() {
        assertThrows(IllegalArgumentException.class, () -> CTraderPriceDecoder.decode(1, -1));
        assertThrows(IllegalArgumentException.class, () -> CTraderPriceDecoder.decode(1, 19));
    }
}
