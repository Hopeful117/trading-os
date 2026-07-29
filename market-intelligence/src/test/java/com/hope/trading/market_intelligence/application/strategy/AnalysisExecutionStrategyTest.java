package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisExecutionStrategyTest {
    private final IntelligenceAnalysisRequest passiveRequest = request(AnalysisExecutionMode.PASSIVE);
    private final IntelligenceAnalysisRequest activeRequest = request(AnalysisExecutionMode.ACTIVE);

    @Test
    void passiveStrategyIsBoundedAndSelectsLightweightContext() {
        AnalysisExecutionPlan plan = new PassiveAnalysisStrategy(
                Duration.ofMillis(750)
        ).plan(passiveRequest);

        assertThat(plan.maximumCapabilities()).isEqualTo(2);
        assertThat(plan.capabilityIds()).hasSizeLessThanOrEqualTo(2);
        assertThat(plan.baselineContext())
                .extracting(ContextRequirement::sectionType)
                .containsExactlyInAnyOrder(
                        ContextSectionType.MARKET_IDENTITY,
                        ContextSectionType.MARKET_SNAPSHOT
                )
                .doesNotContain(ContextSectionType.ACCOUNT);
    }

    @Test
    void activeStrategySelectsBroaderOptionalContextAndBudget() {
        AnalysisExecutionPlan plan = new ActiveAnalysisStrategy(
                Duration.ofSeconds(3)
        ).plan(activeRequest);

        assertThat(plan.maximumCapabilities()).isGreaterThan(2);
        assertThat(plan.capabilityIds()).hasSizeGreaterThan(2);
        assertThat(plan.baselineContext())
                .extracting(ContextRequirement::sectionType)
                .contains(
                        ContextSectionType.HISTORICAL_OHLC,
                        ContextSectionType.ORDER_FLOW,
                        ContextSectionType.NEWS
                )
                .doesNotContain(ContextSectionType.ACCOUNT);
        assertThat(plan.timeout()).isEqualTo(Duration.ofSeconds(3));
    }

    private IntelligenceAnalysisRequest request(AnalysisExecutionMode mode) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), mode, null
        );
    }
}
