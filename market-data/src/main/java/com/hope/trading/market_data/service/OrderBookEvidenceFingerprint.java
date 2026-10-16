package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookEvidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class OrderBookEvidenceFingerprint {
    public static final String NORMALIZATION_VERSION = "order-book-normalization-v1";

    private OrderBookEvidenceFingerprint() {
    }

    public static String fingerprint(OrderBookEvidence evidence) {
        String value = String.join("|",
                evidence.marketId().toString(), evidence.provider().name(), evidence.symbol(),
                String.valueOf(evidence.providerSymbol()), String.valueOf(evidence.depth()),
                evidence.evidenceType().name(), evidence.origin().name(),
                String.valueOf(evidence.sourceStartOrdinal()), String.valueOf(evidence.sourceEndOrdinal()),
                evidence.sequenceId().toString(),
                String.valueOf(evidence.sequenceOrdinal()), String.valueOf(evidence.providerSequence()),
                evidence.occurredAt().toString(), evidence.knownAt().toString(),
                OrderBookEvidencePayload.encode(evidence.bids()),
                OrderBookEvidencePayload.encode(evidence.asks()), String.valueOf(evidence.checksum()),
                evidence.normalizationVersion());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
