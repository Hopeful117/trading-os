package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.OhlcEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HistoricalOhlcEvidenceFingerprint {
    public static final String NORMALIZATION_VERSION = "historical-ohlc-v1";

    private HistoricalOhlcEvidenceFingerprint() {
    }

    public static String captured(OhlcEvent event) {
        return fingerprint(event, HistoricalOhlcEvidenceOrigin.CAPTURED, NORMALIZATION_VERSION);
    }

    public static String fingerprint(
            OhlcEvent event,
            HistoricalOhlcEvidenceOrigin origin,
            String normalizationVersion
    ) {
        String material = String.join("|",
                value(event.marketId()), value(event.interval()), value(event.openTime()),
                value(event.closeTime()), value(event.open()), value(event.high()),
                value(event.low()), value(event.close()), value(event.volume()),
                value(event.vwap()), value(event.trades()), value(event.closed()),
                value(event.synthetic()), value(event.provider()), value(event.symbol()),
                value(event.sourceId()), value(origin), value(normalizationVersion));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "<null>" : value.toString();
    }
}
