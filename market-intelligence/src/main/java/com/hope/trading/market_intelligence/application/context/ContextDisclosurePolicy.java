package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.context.*;
import com.hope.trading.market_intelligence.domain.security.ServiceIdentity;

/**
 * Authorization boundary evaluated after a capability requests context and
 * before any section is transmitted to an external analysis consumer.
 */
public interface ContextDisclosurePolicy {
    ContextDisclosureDecision authorize(
            ContextContributionDescriptor contribution,
            ContextConsumer consumer,
            ContextClassification maximumClassification,
            ServiceIdentity identity
    );
}
