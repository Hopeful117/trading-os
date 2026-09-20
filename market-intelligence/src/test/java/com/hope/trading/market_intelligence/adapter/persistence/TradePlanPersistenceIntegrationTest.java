package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
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

    @Test
    void mappingPreservesManualOriginAndAuthenticatedAuthor() {
        var environment = TradePlanTestFixtures.environment();
        TradePlanningResult.Success result = (TradePlanningResult.Success)
                environment.service().createManual(new ManualTradePlanningRequest(
                        environment.context().id(), environment.context().version(),
                        environment.owner(), "BTC/EUR", TradeDirection.LONG,
                        new EntryStrategy(EntryType.LIMIT, BigDecimal.valueOf(100), Set.of()),
                        new StopLoss(BigDecimal.valueOf(99), "manual invalidation"),
                        List.of(new TakeProfit(BigDecimal.valueOf(102), BigDecimal.valueOf(100))),
                        new PositionSizing(BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.ONE, "EUR"),
                        BigDecimal.valueOf(100), TradePlanTestFixtures.NOW.plusSeconds(3600),
                        "MANUAL_VALIDITY", "Human discretionary setup",
                        Set.of("Price confirms setup"), Set.of("Stop is reached"), Set.of()));

        TradePlanMapper mapper = new TradePlanMapper();
        TradePlan rehydrated = mapper.toDomain(mapper.toEntity(result.plan()));

        assertThat(rehydrated.origin()).isEqualTo(TradePlanOrigin.MANUAL);
        assertThat(rehydrated.authorId()).contains(environment.owner());
        assertThat(rehydrated.rationale().opportunities()).isEmpty();
    }
}
