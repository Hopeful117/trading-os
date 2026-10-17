package com.hope.trading.broker_service.broker.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HistoricalFeeScheduleFingerprint {
    public static final String NORMALIZATION_VERSION = "fee-schedule-v3";

    private HistoricalFeeScheduleFingerprint() {
    }

    public static String fingerprint(HistoricalFeeSchedule schedule) {
        String material = canonicalMaterial(
                schedule.brokerAccountId().toString(), schedule.provider().name(), schedule.instrument(),
                schedule.productType(),
                schedule.effectiveFrom().toString(), String.valueOf(schedule.effectiveTo()),
                schedule.makerRate().toPlainString(), schedule.takerRate().toPlainString(),
                schedule.rateUnit().name(), schedule.settlementAsset(), schedule.sourceId(), schedule.observedAt().toString(),
                schedule.origin().name(), schedule.normalizationVersion());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String canonicalMaterial(String... values) {
        StringBuilder material = new StringBuilder();
        for (String value : values) {
            material.append(value.length()).append(':').append(value);
        }
        return material.toString();
    }
}
