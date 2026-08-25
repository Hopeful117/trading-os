package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0029: the setup snapshot rides the existing payload JSON column.
 * New rows serialize the snapshot; legacy payloads (written before the
 * Story) deserialize with a null snapshot and never fail.
 */
class TradingOpportunitySetupSnapshotPersistenceTest {

    private static final Instant NOW = OpportunityTestFixtures.NOW;

    private static TradingOpportunity opportunityWithSnapshot(
            OpportunitySetupSnapshot snapshot) {
        return new OpportunityFactory().create(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, "BTC/EUR", OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "Confirmed", Set.of(new ObservationReference(UUID.randomUUID())), Set.of(),
                NOW, NOW, NOW.plusSeconds(300), NOW,
                UUID.randomUUID(), snapshot);
    }

    @Test
    void payloadJsonRoundTripPreservesSetupSnapshot() throws Exception {
        OpportunitySetupSnapshot snapshot = new OpportunitySetupSnapshot(
                new BigDecimal("64120.50"), NOW.minusSeconds(60),
                "Price broke resistance with momentum",
                List.of(new OpportunityTrigger("directional_price_change", "12.5")),
                NOW.minusSeconds(60));
        TradingOpportunity value = opportunityWithSnapshot(snapshot);

        JpaTradingOpportunityEntity entity = invokeEntity(value);
        assertThat(entity.payload).contains("referencePrice");
        assertThat(entity.payload).contains("directional_price_change");

        TradingOpportunity restored = invokeDomain(entity);
        assertThat(restored.setup()).contains(snapshot);
    }

    @Test
    void legacyPayloadWithoutSnapshotDeserializesToNull() throws Exception {
        String legacyPayload = """
                {"id":"%s","version":1,"status":"ACTIVE","instrument":"BTC/EUR",
                 "direction":"LONG","scenario":"Legacy OHLC Trend","timeframe":"m15",
                 "type":"POSITIONAL","origin":"USER_REQUEST","score":100,
                 "explanation":"Legacy OHLC Trend",
                 "observationIds":["11111111-1111-1111-1111-111111111111"],
                 "aiAnalysisIds":[],
                 "evaluatedAt":"2026-08-25T20:30:00Z","validFrom":"2026-08-25T20:30:00Z",
                 "validUntil":"2026-08-25T21:00:00Z","createdAt":"2026-08-25T20:20:26Z",
                 "strategyMatchId":"22222222-2222-2222-2222-222222222222"}
                """.formatted(UUID.randomUUID());

        var domain = new TradingOpportunityMapper()
                .toDomain(new ObjectMapper().registerModule(new JavaTimeModule())
                        .readValue(legacyPayload, TradingOpportunityEntity.class));

        assertThat(domain.setup()).isEmpty();
        assertThat(domain.instrument()).isEqualTo("BTC/EUR");
        assertThat(domain.strategyMatchId()).isPresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JpaTradingOpportunityEntity invokeEntity(TradingOpportunity value)
            throws Exception {
        var method = JpaTradingOpportunityRepository.class
                .getDeclaredMethod("entity", TradingOpportunity.class);
        method.setAccessible(true);
        return (JpaTradingOpportunityEntity) method.invoke(
                new JpaTradingOpportunityRepository(null,
                        new ObjectMapper().registerModule(new JavaTimeModule())), value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TradingOpportunity invokeDomain(JpaTradingOpportunityEntity entity)
            throws Exception {
        var method = JpaTradingOpportunityRepository.class
                .getDeclaredMethod("domain", JpaTradingOpportunityEntity.class);
        method.setAccessible(true);
        return (TradingOpportunity) method.invoke(
                new JpaTradingOpportunityRepository(null,
                        new ObjectMapper().registerModule(new JavaTimeModule())), entity);
    }
}
