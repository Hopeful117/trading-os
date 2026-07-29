package com.hope.trading.market_intelligence.domain.context;

import com.hope.trading.market_intelligence.domain.ContextSectionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextClassificationTest {
    @Test
    void classificationOrderEnforcesMaximumDisclosure() {
        assertThat(ContextClassification.PUBLIC.isAllowedBy(
                ContextClassification.TRADING_SENSITIVE
        )).isTrue();
        assertThat(ContextClassification.RESTRICTED.isAllowedBy(
                ContextClassification.TRADING_SENSITIVE
        )).isFalse();
    }

    @Test
    void restrictedContributionIsNeverExposedToAConsumer() {
        ContextContributionDescriptor descriptor = new ContextContributionDescriptor(
                Set.of(ContextSectionType.ACCOUNT),
                ContextClassification.RESTRICTED,
                Duration.ofSeconds(1),
                Set.of(ContextConsumer.AI_CAPABILITY)
        );

        assertThat(descriptor.permits(ContextConsumer.AI_CAPABILITY)).isFalse();
        assertThat(descriptor.providedSections()).containsExactly(
                ContextSectionType.ACCOUNT
        );
    }
}
