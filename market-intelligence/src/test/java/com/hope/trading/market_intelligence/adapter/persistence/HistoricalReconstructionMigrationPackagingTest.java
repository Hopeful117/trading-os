package com.hope.trading.market_intelligence.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalReconstructionMigrationPackagingTest {
    @Test
    void lifecycleMigrationIsAvailableOnTheRuntimeClasspath() {
        assertThat(getClass().getResource(
                "/db/migration/V20__historical_reconstruction_lifecycle.sql"))
                .isNotNull();
    }
}
