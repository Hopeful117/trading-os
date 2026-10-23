package com.hope.trading.broker_service.ctrader.authentication;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class CTraderTokenCodec {
    private static final String VERSION = "v1";

    private CTraderTokenCodec() {
    }

    public static byte[] encode(CTraderToken token) {
        return String.join("\n", VERSION, token.accessToken(), token.refreshToken(), token.expiresAt().toString())
                .getBytes(StandardCharsets.UTF_8);
    }

    public static CTraderToken decode(byte[] value) {
        String[] fields = new String(value, StandardCharsets.UTF_8).split("\n", -1);
        if (fields.length != 4 || !VERSION.equals(fields[0])) {
            throw new CTraderAuthenticationException("Stored cTrader token material is invalid");
        }
        return new CTraderToken(fields[1], fields[2], Instant.parse(fields[3]));
    }
}
