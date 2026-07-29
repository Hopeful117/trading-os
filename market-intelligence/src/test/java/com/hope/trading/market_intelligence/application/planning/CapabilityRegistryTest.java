package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CapabilityRegistryTest {
    @Test
    void rejectsDuplicateIdentityAndVersion() {
        Capability first = CapabilityTestFixtures.capability(
                "same", List.of(), List.of(), RetryPolicy.disabled(),
                context -> CapabilityResult.noOpportunity(List.of()));
        Capability duplicate = CapabilityTestFixtures.capability(
                "same", List.of(), List.of(), RetryPolicy.disabled(),
                context -> CapabilityResult.noOpportunity(List.of()));
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(first);

        assertThatThrownBy(() -> registry.register(duplicate))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
