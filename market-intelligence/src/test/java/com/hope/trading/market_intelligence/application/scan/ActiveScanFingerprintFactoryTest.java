package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveScanFingerprintFactoryTest {
    private final ActiveScanFingerprintFactory factory =
            new ActiveScanFingerprintFactory(new ObjectMapper());

    @Test
    void fingerprintIsStableUnderObjectiveTrimAndRequestedDeduplication() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketA = UUID.randomUUID();
        UUID marketB = UUID.randomUUID();

        String first = factory.fingerprint(
                actorId,
                accountId,
                "  breakout  ",
                Arrays.asList(marketA, null, marketB, marketA)
        );
        String second = factory.fingerprint(
                actorId,
                accountId,
                "breakout",
                List.of(marketA, marketB)
        );

        assertThat(first).isEqualTo(second);
    }

    @Test
    void fingerprintChangesWhenLogicalRequestChanges() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();

        String first = factory.fingerprint(actorId, accountId, "scan", List.of(marketId));
        String second = factory.fingerprint(actorId, accountId, "other", List.of(marketId));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void normalizationPreservesFirstOccurrenceOrder() {
        UUID marketA = UUID.randomUUID();
        UUID marketB = UUID.randomUUID();

        assertThat(factory.normalizeRequestedMarketIds(Arrays.asList(marketA, marketB, marketA, null)))
                .containsExactly(marketA, marketB);
    }
}
