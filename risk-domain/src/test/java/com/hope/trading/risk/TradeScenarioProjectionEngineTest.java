package com.hope.trading.risk;

import com.hope.trading.risk.projection.TradeScenarioProjection;
import com.hope.trading.risk.projection.TradeScenarioProjectionEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeScenarioProjectionEngineTest {
    private final TradeScenarioProjectionEngine engine = new TradeScenarioProjectionEngine();

    @Test
    void projectsLongStopTargetAndRiskReward() {
        TradeScenarioProjection value = engine.project(input("LONG", "100", "95", "110"), Instant.EPOCH);

        assertAmount("10", value.economics().monetaryRisk());
        assertAmount("20", value.economics().monetaryReward());
        assertAmount("2", value.economics().riskReward());
        assertAmount("-10", value.stop().projectedPnl());
        assertAmount("20", value.target().projectedPnl());
        assertAmount("9990", value.stop().projectedBalance());
        assertAmount("10020", value.target().projectedBalance());
    }

    @Test
    void projectsShortStopAndTarget() {
        TradeScenarioProjection value = engine.project(input("SHORT", "100", "105", "90"), Instant.EPOCH);

        assertAmount("-10", value.stop().projectedPnl());
        assertAmount("20", value.target().projectedPnl());
    }

    @Test
    void keepsTargetAvailableWhenStopIsMissing() {
        TradeScenarioProjection value = engine.project(input("LONG", "100", null, "110"), Instant.EPOCH);

        assertEquals(TradeScenarioProjection.Availability.UNAVAILABLE, value.stop().availability());
        assertEquals(TradeScenarioProjection.Availability.AVAILABLE, value.target().availability());
        assertNull(value.economics().riskReward());
    }

    @Test
    void rejectsContradictoryGeometryWithoutGuessing() {
        TradeScenarioProjection value = engine.project(input("LONG", "100", "105", "90"), Instant.EPOCH);

        assertEquals(TradeScenarioProjection.Availability.UNAVAILABLE, value.economics().availability());
        assertTrue(value.economics().reasons().containsAll(
                java.util.List.of("STOP_GEOMETRY_INVALID", "TARGET_GEOMETRY_INVALID")));
        assertNull(value.stop().projectedBalance());
    }

    @Test
    void preservesPartialAvailabilityWhenHeadroomIsUnavailable() {
        TradeScenarioProjectionEngine.Input input = new TradeScenarioProjectionEngine.Input(
                "LONG", bd("100"), bd("95"), bd("110"), bd("2"), bd("10000"), bd("10000"),
                 null, null, null, null, bd("10000"), bd("11000"), bd("1"), true, false,
                 "RISK_HEADROOM_UNAVAILABLE");

        TradeScenarioProjection value = engine.project(input, Instant.EPOCH);

        assertEquals(TradeScenarioProjection.Availability.PARTIAL, value.stop().availability());
        assertAmount("-10", value.stop().projectedPnl());
        assertAmount("1010", value.stop().challengeTargetRemaining());
    }

    @Test
    void marksChallengeAsNotApplicableWhenNoChallengeExists() {
        TradeScenarioProjectionEngine.Input input = new TradeScenarioProjectionEngine.Input(
                "LONG", bd("100"), bd("95"), bd("110"), bd("2"), bd("10000"), bd("10000"),
                 null, null, null, null, null, null, bd("1"), false, false, null);

        assertEquals(TradeScenarioProjection.Availability.NOT_APPLICABLE,
                engine.project(input, Instant.EPOCH).challenge().availability());
    }

    @Test
    void failsClosedWhenQuoteConversionIsUnavailable() {
        TradeScenarioProjectionEngine.Input input = new TradeScenarioProjectionEngine.Input(
                "LONG", bd("100"), bd("95"), bd("110"), bd("2"), bd("10000"), bd("10000"),
                bd("500"), bd("1000"), bd("500"), bd("1000"), bd("10000"), bd("11000"), null, true,
                true, null);

        TradeScenarioProjection value = engine.project(input, Instant.EPOCH);

        assertEquals(TradeScenarioProjection.Availability.UNAVAILABLE,
                value.economics().availability());
        assertTrue(value.economics().reasons().contains("CURRENCY_CONVERSION_UNAVAILABLE"));
        assertNull(value.stop().projectedPnl());
    }

    private TradeScenarioProjectionEngine.Input input(String direction, String entry,
                                                       String stop, String target) {
        return new TradeScenarioProjectionEngine.Input(direction, bd(entry), nullable(stop), nullable(target),
                bd("2"), bd("10000"), bd("10000"), bd("500"), bd("1000"), bd("500"), bd("1000"),
                bd("10000"), bd("11000"), bd("1"), true, true, null);
    }

    private BigDecimal nullable(String value) { return value == null ? null : bd(value); }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
