package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.context.*;
import com.hope.trading.market_intelligence.domain.security.ServiceIdentity;
import org.springframework.stereotype.Component;

@Component
public class DefaultContextDisclosurePolicy implements ContextDisclosurePolicy {
    @Override
    public ContextDisclosureDecision authorize(
            ContextContributionDescriptor contribution,
            ContextConsumer consumer,
            ContextClassification maximumClassification,
            ServiceIdentity identity
    ) {
        if (!ServiceIdentity.marketIntelligence().equals(identity)) {
            return ContextDisclosureDecision.deny("CALLER_NOT_AUTHORIZED");
        }
        if (!contribution.classification().isAllowedBy(maximumClassification)) {
            return ContextDisclosureDecision.deny("CLASSIFICATION_EXCEEDS_POLICY");
        }
        if (!contribution.permits(consumer)) {
            return ContextDisclosureDecision.deny("CONSUMER_NOT_AUTHORIZED");
        }
        return ContextDisclosureDecision.allow();
    }
}
