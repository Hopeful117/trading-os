package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ml01DukascopyPartitioningTest {
    private static final Instant TRAIN_END = Instant.parse("2026-06-13T12:00:00Z");
    private static final Instant VALIDATION_END = Instant.parse("2026-08-07T06:00:00Z");
    private static final Instant TEST_END = Instant.parse("2026-10-01T00:00:00Z");
    private static final Ml01DukascopyPartitioning.Scenario SCENARIO =
            new Ml01DukascopyPartitioning.Scenario("COMMON_UTC_70_15_15", TRAIN_END, VALIDATION_END, TEST_END);

    @Test
    void assignsThreeChronologicalPopulationsUsingCommonUtcBoundaries() {
        Duration noEmbargo = Duration.ZERO;

        assertThat(Ml01DukascopyPartitioning.assign(TRAIN_END.minusSeconds(1), SCENARIO, noEmbargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("TRAIN", null));
        assertThat(Ml01DukascopyPartitioning.assign(TRAIN_END, SCENARIO, noEmbargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("VALIDATION", null));
        assertThat(Ml01DukascopyPartitioning.assign(VALIDATION_END, SCENARIO, noEmbargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("TEST", null));
        assertThat(Ml01DukascopyPartitioning.assign(TEST_END, SCENARIO, noEmbargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("OUT_OF_RANGE", "OUTSIDE_PARTITION_BOUNDS"));
    }

    @Test
    void appliesEmbargoWithoutCollapsingValidationAndTestIntoTrain() {
        Duration embargo = Duration.ofHours(24);

        assertThat(Ml01DukascopyPartitioning.assign(TRAIN_END.plusSeconds(12 * 3600L), SCENARIO, embargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("VALIDATION", "EMBARGO_AFTER_TRAIN"));
        assertThat(Ml01DukascopyPartitioning.assign(VALIDATION_END.plusSeconds(12 * 3600L), SCENARIO, embargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("TEST", "EMBARGO_AFTER_VALIDATION"));
        assertThat(Ml01DukascopyPartitioning.assign(VALIDATION_END.plusSeconds(24 * 3600L), SCENARIO, embargo))
                .isEqualTo(new Ml01DukascopyPartitioning.Assignment("TEST", null));
    }

    @Test
    void rejectsNonChronologicalOrNegativeConfiguration() {
        assertThatThrownBy(() -> new Ml01DukascopyPartitioning.Scenario(
                "invalid", VALIDATION_END, TRAIN_END, TEST_END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ml01DukascopyPartitioning.assign(TRAIN_END, SCENARIO, Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
