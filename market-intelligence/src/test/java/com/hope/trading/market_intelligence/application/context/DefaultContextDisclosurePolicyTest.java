package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.context.*;
import com.hope.trading.market_intelligence.domain.security.ServiceIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultContextDisclosurePolicyTest {
    private final DefaultContextDisclosurePolicy policy =
            new DefaultContextDisclosurePolicy();

    @Test
    void classificationAndAllowedConsumerBothGovernDisclosure() {
        ContextContributionDescriptor confidential =
                new ContextContributionDescriptor(
                        Set.of(ContextSectionType.ACCOUNT),
                        ContextClassification.USER_CONFIDENTIAL,
                        Duration.ofSeconds(10),
                        Set.of(ContextConsumer.DETERMINISTIC_CAPABILITY)
                );

        assertThat(policy.authorize(
                confidential,
                ContextConsumer.AI_CAPABILITY,
                ContextClassification.TRADING_SENSITIVE,
                ServiceIdentity.marketIntelligence()
        ).allowed()).isFalse();
        assertThat(policy.authorize(
                confidential,
                ContextConsumer.DETERMINISTIC_CAPABILITY,
                ContextClassification.INTERNAL,
                ServiceIdentity.marketIntelligence()
        ).allowed()).isFalse();
        assertThat(policy.authorize(
                confidential,
                ContextConsumer.DETERMINISTIC_CAPABILITY,
                ContextClassification.TRADING_SENSITIVE,
                ServiceIdentity.marketIntelligence()
        ).allowed()).isTrue();
    }
}
