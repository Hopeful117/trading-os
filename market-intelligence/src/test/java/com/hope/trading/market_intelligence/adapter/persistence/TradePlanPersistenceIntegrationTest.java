package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TradePlanPersistenceIntegrationTest {
    @Test
    void mappingAndAppendOnlyRepositoryPreserveCompleteAggregateAndHistory() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan first = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        TradePlan second = environment.service().transition(
                first.id(), TradePlanStatus.ACCEPTED);

        assertThat(environment.plans().find(
                first.id(), first.version()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(first);
        assertThat(environment.plans().findLatest(first.id()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(second);
        assertThatThrownBy(() -> environment.plans().append(second))
                .isInstanceOf(IllegalStateException.class);
    }
}
