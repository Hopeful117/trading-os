package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OpportunityTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    public static Observation observation() {
        return new ObservationFactory().create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("BREAKOUT"),
                "Breakout", "Confirmed breakout", Set.of("structure"), "5m",
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(600),
                null, "rule-v1",
                List.of(ObservationTestFixtures.evidence(new BigDecimal("0.80"))));
    }

    public static final UUID MATCH_ID = UUID.fromString("cccccccc-9999-8888-7777-666666666666");

    public static CreateOpportunityCommand command(Observation observation) {
        return command(observation, MATCH_ID);
    }

    public static CreateOpportunityCommand command(Observation observation, UUID matchId) {
        return new CreateOpportunityCommand(
                "BTC/EUR", OpportunityDirection.LONG, "Bullish breakout", "5m",
                OpportunityOrigin.PASSIVE_SCAN,
                Set.of(new ObservationReference(observation.id())), Set.of(),
                NOW, NOW.plusSeconds(300), matchId, null, null);
    }

    public static TradingOpportunity opportunity(
            OpportunityId id, long version, OpportunityStatus status,
            OpportunityScore score, Instant evaluatedAt) {
        return new OpportunityFactory().create(
                id, new OpportunityVersion(version), status, "BTC/EUR",
                OpportunityDirection.LONG, "Bullish breakout", "5m",
                OpportunityType.SCALPING, OpportunityOrigin.PASSIVE_SCAN, score,
                "Confirmed", Set.of(new ObservationReference(UUID.randomUUID())), Set.of(),
                evaluatedAt, evaluatedAt, evaluatedAt.plusSeconds(300), evaluatedAt,
                null, null);
    }

    private OpportunityTestFixtures() {}
}
