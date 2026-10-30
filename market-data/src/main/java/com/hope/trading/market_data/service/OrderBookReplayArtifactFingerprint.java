package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

final class OrderBookReplayArtifactFingerprint {
    private OrderBookReplayArtifactFingerprint() {
    }

    static String calculate(OrderBookReplayResult replay, String semanticIdentity, String promotionReason,
                             String signingKey) {
        return digest(String.join("|", semanticIdentity, promotionReason, replay.marketId().toString(),
                Integer.toString(replay.depth()), replay.sequenceId().toString(), replay.evaluationCutoff().toString(),
                String.valueOf(replay.sourceStartOrdinal()), String.valueOf(replay.sourceEndOrdinal()),
                replay.provider(), replay.providerSymbol(), replay.normalizationVersion(),
                replay.sourceEvidenceFingerprint(), String.join(",", replay.sourceEvidenceFingerprints()),
                replay.sourceTrustStatus(), replay.sourceEvidenceOrigin().name(),
                replay.latestOccurredAt().toString(), replay.latestKnownAt().toString(),
                replay.bids().toString(), replay.asks().toString()), replay.evaluationCutoff(), signingKey);
    }

    static String calculate(OrderBookReplayArtifact artifact, String signingKey) {
        String sourceTrustMaterial = "order-book-replay-artifact-v3".equals(artifact.artifactSchemaVersion())
                ? artifact.sourceTrustStatus() + "|" + artifact.sourceEvidenceOrigin().name()
                : artifact.sourceTrustStatus();
        String material = String.join("|", artifact.semanticIdentity(), artifact.promotionReason(),
                artifact.marketId().toString(), Integer.toString(artifact.depth()), artifact.sequenceId().toString(),
                artifact.evaluationCutoff().toString(), String.valueOf(artifact.sourceStartOrdinal()),
                String.valueOf(artifact.sourceEndOrdinal()), artifact.provider(), artifact.providerSymbol(),
                artifact.normalizationVersion(), artifact.sourceEvidenceFingerprint(),
                String.join(",", artifact.sourceEvidenceFingerprints()), sourceTrustMaterial,
                artifact.latestOccurredAt().toString(), artifact.latestKnownAt().toString(),
                artifact.bids().toString(), artifact.asks().toString());
        return digest(material, artifact.evaluationCutoff(), signingKey);
    }

    static boolean matches(OrderBookReplayArtifact artifact, String signingKey) {
        return MessageDigest.isEqual(
                calculate(artifact, signingKey).getBytes(StandardCharsets.UTF_8),
                artifact.artifactFingerprint().getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String material, Instant timestamp, String signingKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((material + "|" + timestamp).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is required", exception);
        }
    }
}
