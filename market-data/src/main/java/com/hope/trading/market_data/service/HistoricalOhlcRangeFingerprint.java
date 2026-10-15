package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcGap;
import com.hope.trading.market_data.model.HistoricalOhlcRange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

public final class HistoricalOhlcRangeFingerprint {
    private static final String CONTRACT_VERSION = "historical-ohlc-range-v1";
    private static final String SELECTION_POLICY = "known-at-captured-observation-id-v1";

    private HistoricalOhlcRangeFingerprint() {
    }

    public static String fingerprint(HistoricalOhlcRange range) {
        StringBuilder material = new StringBuilder()
                .append(CONTRACT_VERSION).append('|')
                .append(range.marketId()).append('|')
                .append(range.interval()).append('|')
                .append(range.fromExclusive()).append('|')
                .append(range.toInclusive()).append('|')
                .append(range.evidencePolicy()).append('|')
                .append(SELECTION_POLICY).append('|')
                .append(range.expectedObservationCount()).append('|')
                .append(range.observedObservationCount()).append('|');

        range.observations().stream()
                .sorted(Comparator.comparing(HistoricalOhlcEvidence::openTime)
                        .thenComparing(HistoricalOhlcEvidence::observationFingerprint))
                .forEach(observation -> material.append("observation:")
                        .append(observation.openTime()).append(':')
                        .append(observation.closeTime()).append(':')
                        .append(observation.observationFingerprint()).append('|'));
        range.gaps().stream()
                .sorted(Comparator.comparing(HistoricalOhlcGap::openTime))
                .forEach(gap -> material.append("gap:")
                        .append(gap.openTime()).append(':')
                        .append(gap.closeTime()).append('|'));
        range.normalizationVersions().stream().sorted().distinct()
                .forEach(version -> material.append("normalization:").append(version).append('|'));

        return sha256(material.toString());
    }

    private static String sha256(String material) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
