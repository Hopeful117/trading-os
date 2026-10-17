package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalFundingEvidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HistoricalFundingEvidenceFingerprint {
    public static final String NORMALIZATION_VERSION = "funding-evidence-normalization-v2";

    private HistoricalFundingEvidenceFingerprint() {
    }

    public static String fingerprint(HistoricalFundingEvidence evidence) {
        String value = String.join("|",
                evidence.marketId().toString(), evidence.provider().name(), evidence.symbol(),
                evidence.providerSymbol(), evidence.productType().name(), evidence.fundingTime().toString(),
                evidence.effectiveFrom().toString(), String.valueOf(evidence.effectiveTo()),
                evidence.fundingRate().toPlainString(), evidence.settlementAsset(),
                String.valueOf(evidence.sourceUpdatedAt()),
                evidence.sourceId(), evidence.origin().name(), evidence.normalizationVersion());
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
