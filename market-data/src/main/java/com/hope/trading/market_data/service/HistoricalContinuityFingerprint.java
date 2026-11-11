package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalClosureEvidence;
import com.hope.trading.market_data.model.HistoricalContinuityPolicy;
import com.hope.trading.market_data.model.HistoricalOhlcTransition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

public final class HistoricalContinuityFingerprint {
    private HistoricalContinuityFingerprint() {
    }

    public static String fingerprint(HistoricalContinuityPolicy policy,
                                     List<HistoricalOhlcTransition> transitions) {
        StringBuilder material = new StringBuilder("historical-continuity-v1|")
                .append(policy.policyVersion()).append('|')
                .append(policy.marketId()).append('|')
                .append(policy.canonicalSymbol()).append('|')
                .append(policy.venue()).append('|')
                .append(policy.interval()).append('|')
                .append(policy.evidenceCutoff()).append('|')
                .append(policy.allowVerifiedClosures()).append('|');
        (transitions == null ? List.<HistoricalOhlcTransition>of() : transitions).stream()
                .sorted(Comparator.comparing(HistoricalOhlcTransition::previousCloseTime))
                .forEach(transition -> material.append(transition.previousCloseTime()).append('|')
                        .append(transition.nextOpenTime()).append('|')
                        .append(transition.classification()).append('|')
                        .append(closureFingerprint(transition.closureEvidence())).append('|'));
        return sha256(material.toString());
    }

    private static String closureFingerprint(HistoricalClosureEvidence evidence) {
        return evidence == null ? "none" : evidence.fingerprint();
    }

    private static String sha256(String material) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
