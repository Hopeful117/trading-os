package com.hope.trading.market_intelligence.adapter.web;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchExecutionResponseTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @Test
    void exposesRetryProgressAndReportAvailabilityWithoutSensitiveDetails() {
        ResearchExecution execution = ResearchExecution.requested(
                        UUID.randomUUID(), UUID.randomUUID(), "key", "fingerprint",
                        List.of(UUID.randomUUID(), UUID.randomUUID()), "compare", "STANDARD", NOW)
                .running(NOW.plusSeconds(1))
                .checkpoint(JsonNodeFactory.instance.objectNode().put("completedMarkets", 1), NOW.plusSeconds(2))
                .requeue(NOW.plusSeconds(301));

        ResearchExecutionResponse response = ResearchExecutionResponse.from(execution);

        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.currentStage()).isEqualTo(ResearchExecutionStage.REQUEUED);
        assertThat(response.retrying()).isTrue();
        assertThat(response.reportAvailable()).isTrue();
        assertThat(response.failureCategory()).isNull();
    }

    @Test
    void mapsFailureCodesToBoundedCategories() {
        ResearchExecutionResponse response = ResearchExecutionResponse.from(
                ResearchExecution.requested(UUID.randomUUID(), UUID.randomUUID(), "key", "fingerprint",
                                List.of(UUID.randomUUID(), UUID.randomUUID()), "compare", "STANDARD", NOW)
                        .running(NOW.plusSeconds(1))
                        .fail("RESEARCH_EXECUTION_EXPIRED", NOW.plusSeconds(2)));

        assertThat(response.failureCategory()).isEqualTo("TIMEOUT");
        assertThat(response.failureCode()).isEqualTo("RESEARCH_EXECUTION_EXPIRED");
    }
}
