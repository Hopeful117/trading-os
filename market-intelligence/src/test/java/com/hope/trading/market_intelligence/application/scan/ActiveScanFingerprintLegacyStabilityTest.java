package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0243: legacy ActiveScan requests must keep the exact fingerprint they had
 * before passive-candidate fields were introduced, otherwise persisted
 * idempotency keys would report false conflicts after deployment.
 * The pinned value was computed on base commit 7aa7a2a8 before any change.
 */
class ActiveScanFingerprintLegacyStabilityTest {
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID MARKET = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final String LEGACY_HASH = "fd275f3bbaffc4099d565383ac4701e9bfcee58ceea68e000234cff8e757590a";

    private final ActiveScanFingerprintFactory factory = new ActiveScanFingerprintFactory(new ObjectMapper());

    @Test
    void legacyRequestFingerprintIsUnchanged() {
        assertThat(factory.fingerprint(ACTOR, ACCOUNT, "breakout", List.of(MARKET),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED))
                .isEqualTo(LEGACY_HASH);
    }

    @Test
    void passiveCandidateReferenceParticipatesInFingerprint() {
        var reference = new com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference(
                UUID.fromString("00000000-0000-0000-0000-000000000004"), 1);
        String withCandidate = factory.fingerprint(ACTOR, ACCOUNT, "breakout", List.of(MARKET),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED, reference);
        String otherVersion = factory.fingerprint(ACTOR, ACCOUNT, "breakout", List.of(MARKET),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED,
                new com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference(
                        reference.opportunityId(), 2));

        assertThat(withCandidate).isNotEqualTo(LEGACY_HASH).isNotEqualTo(otherVersion);
        assertThat(factory.fingerprint(ACTOR, ACCOUNT, "breakout", List.of(MARKET),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED, null)).isEqualTo(LEGACY_HASH);
    }
}
