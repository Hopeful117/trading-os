package com.hope.trading.market_intelligence.application.tradeplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public final class ManualTradePlanFingerprintFactory {
    private final ObjectMapper mapper;

    public ManualTradePlanFingerprintFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String fingerprint(ManualTradePlanningRequest request) {
        Payload payload = new Payload(request.actorId(), request.tradingAccountId(),
                request.instrument(), request.direction(), request.entry(),
                request.stopLoss(), request.takeProfits(), request.positionSizing(),
                request.referencePrice(), request.expiresAt(), request.expirationPolicy(),
                request.thesis(), new TreeSet<>(request.confirmationConditions()),
                new TreeSet<>(request.invalidationConditions()), new TreeSet<>(request.managementRules()));
        try {
            return sha256(mapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize manual Trade Plan fingerprint", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) result.append(String.format("%02x", current));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Payload(UUID actorId, UUID tradingAccountId, String instrument,
                           Object direction, Object entry, Object stopLoss, Object takeProfits,
                           Object positionSizing, Object referencePrice, Object expiresAt,
                           String expirationPolicy,
                           String thesis, TreeSet<String> confirmations,
                           TreeSet<String> invalidations, TreeSet<String> management) { }
}
