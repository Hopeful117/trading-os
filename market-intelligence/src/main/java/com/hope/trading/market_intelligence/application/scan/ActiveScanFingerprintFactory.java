package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

@Component
public class ActiveScanFingerprintFactory {
    private final ObjectMapper mapper;

    public ActiveScanFingerprintFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String fingerprint(
            UUID actorId, UUID accountId, String objective, List<UUID> requestedMarketIds) {
        return fingerprint(actorId, accountId, objective, requestedMarketIds,
                requestedMarketIds != null && requestedMarketIds.stream().anyMatch(Objects::nonNull)
                        ? MarketScopeMode.SELECTED : null, TradeabilityEvidenceProfile.UNSUPPORTED);
    }

    public String fingerprint(
            UUID actorId,
            UUID accountId,
            String objective,
            List<UUID> requestedMarketIds,
            MarketScopeMode scopeMode
    ) {
        return fingerprint(actorId, accountId, objective, requestedMarketIds, scopeMode,
                TradeabilityEvidenceProfile.UNSUPPORTED);
    }

    public String fingerprint(
            UUID actorId,
            UUID accountId,
            String objective,
            List<UUID> requestedMarketIds,
            MarketScopeMode scopeMode,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile
    ) {
        return fingerprint(actorId, accountId, objective, requestedMarketIds, scopeMode,
                tradeabilityEvidenceProfile, null);
    }

    /**
     * Story 0243: the optional passive candidate reference participates in the
     * fingerprint only when present, so legacy requests keep their exact hash.
     */
    public String fingerprint(
            UUID actorId,
            UUID accountId,
            String objective,
            List<UUID> requestedMarketIds,
            MarketScopeMode scopeMode,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
            com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference passiveCandidate
    ) {
        FingerprintPayload payload = new FingerprintPayload(
                Objects.requireNonNull(actorId).toString(),
                Objects.requireNonNull(accountId).toString(),
                normalizeObjective(objective),
                scopeMode,
                normalizeRequestedMarketIds(requestedMarketIds).stream().map(UUID::toString).toList(),
                tradeabilityEvidenceProfile == null
                        ? TradeabilityEvidenceProfile.UNSUPPORTED : tradeabilityEvidenceProfile,
                passiveCandidate == null ? null : passiveCandidate.opportunityId().toString(),
                passiveCandidate == null ? null : passiveCandidate.opportunityVersion()
        );
        try {
            return sha256Hex(mapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize active scan fingerprint payload", exception);
        }
    }

    public String normalizeObjective(String value) {
        return value == null ? "" : value.strip();
    }

    public List<UUID> normalizeRequestedMarketIds(List<UUID> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return value.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record FingerprintPayload(
            String actorId,
            String accountId,
            String objective,
            MarketScopeMode scopeMode,
            List<String> requestedMarketIds,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sourcePassiveOpportunityId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long sourcePassiveOpportunityVersion
    ) {
        private FingerprintPayload {
            requestedMarketIds = List.copyOf(requestedMarketIds);
        }
    }
}
