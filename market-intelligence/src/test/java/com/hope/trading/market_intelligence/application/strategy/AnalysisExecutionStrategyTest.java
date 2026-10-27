package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.OhlcMomentumAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.TradeabilityAnalysisCapability;
import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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
        assertThat(plan.capabilityIds()).hasSizeLessThanOrEqualTo(3);
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

    @Test
    void strictObservedActivePlanDoesNotRequireCurrentSnapshot() {
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "replay",
                null, null, null, Instant.parse("2026-10-15T12:00:00Z"),
                EvidenceAcquisitionPolicy.STRICT_OBSERVED);

        AnalysisExecutionPlan plan = new ActiveAnalysisStrategy(Duration.ofSeconds(3)).plan(request);

        assertThat(plan.baselineContext()).extracting(ContextRequirement::sectionType)
                .contains(ContextSectionType.HISTORICAL_OHLC, ContextSectionType.TREND_CONTEXT)
                .doesNotContain(ContextSectionType.MARKET_SNAPSHOT);
        assertThat(plan.capabilityIds()).containsExactlyInAnyOrder(
                 TrendContextAnalysisCapability.CAPABILITY_ID,
                 OhlcRangeAnalysisCapability.CAPABILITY_ID,
                 OhlcMomentumAnalysisCapability.CAPABILITY_ID,
                  TradeabilityAnalysisCapability.CAPABILITY_ID);
    }

    @Test
    void strictObservedPassivePlanDoesNotRequireCurrentSnapshot() {
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.PASSIVE, "replay",
                null, null, null, Instant.parse("2026-10-15T12:00:00Z"),
                EvidenceAcquisitionPolicy.STRICT_OBSERVED);

        AnalysisExecutionPlan plan = new PassiveAnalysisStrategy(Duration.ofMillis(750)).plan(request);

        assertThat(plan.baselineContext()).extracting(ContextRequirement::sectionType)
                 .containsExactlyInAnyOrder(
                         ContextSectionType.MARKET_IDENTITY,
                         ContextSectionType.HISTORICAL_OHLC,
                         ContextSectionType.TREND_CONTEXT
                 )
                 .doesNotContain(ContextSectionType.MARKET_SNAPSHOT);
        assertThat(plan.baselineContext())
                .filteredOn(requirement -> requirement.sectionType() == ContextSectionType.MARKET_IDENTITY)
                .singleElement()
                .extracting(ContextRequirement::required)
                .isEqualTo(false);
        assertThat(plan.capabilityIds())
                .containsExactlyInAnyOrder(
                         TrendContextAnalysisCapability.CAPABILITY_ID,
                         OhlcRangeAnalysisCapability.CAPABILITY_ID,
                         OhlcMomentumAnalysisCapability.CAPABILITY_ID);
    }

    private IntelligenceAnalysisRequest request(AnalysisExecutionMode mode) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), mode, null
        );
    }
}
