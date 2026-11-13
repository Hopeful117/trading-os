package com.hope.trading.market_intelligence.application.passive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.stream.Collectors;

final class PassiveScanFingerprintFactory {
    private PassiveScanFingerprintFactory() { }

    static String requestFingerprint(PassiveScanRunRequest request, PassiveMarketUniverse universe) {
        String canonical = String.join("|", request.idempotencyKey(), request.triggerContext(),
                request.plannedWindowStart().toString(), request.plannedWindowEnd().toString(),
                universe.configurationVersion(), universe.fingerprintAlgorithm(), universe.fingerprint(),
                universe.entries().stream().map(entry -> String.join(",", entry.marketId().toString(),
                        entry.provider(), entry.productType(), entry.cadence().toString(),
                        entry.minimumEvidenceProfile().name())).collect(Collectors.joining(";")));
        return sha256(canonical);
    }

    static String attemptKey(java.util.UUID runId, java.util.UUID marketId, int attempt) {
        return "passive-scan-v1:" + runId + ":market:" + marketId + ":attempt:" + attempt;
    }

    static String diagnostic(PassiveMarketUniverseDiagnostic diagnostic) {
        return diagnostic.code().name() + ":" + diagnostic.message();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
