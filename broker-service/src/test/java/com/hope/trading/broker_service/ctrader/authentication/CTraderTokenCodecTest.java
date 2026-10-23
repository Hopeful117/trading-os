package com.hope.trading.broker_service.ctrader.authentication;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CTraderTokenCodecTest {
    @Test
    void roundTripsTokenMaterialWithoutExposingItInToString() {
        CTraderToken token = new CTraderToken("access-value", "refresh-value",
                Instant.parse("2026-11-22T16:00:00Z"));

        CTraderToken decoded = CTraderTokenCodec.decode(CTraderTokenCodec.encode(token));

        assertEquals(token, decoded);
        assertThrows(CTraderAuthenticationException.class,
                () -> CTraderTokenCodec.decode("invalid".getBytes()));
    }
}
