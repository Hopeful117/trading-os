package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotRequest;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpportunityTradePlanGenerationServiceTest {
    private MarketDataClient marketData;
    private OpportunityTradePlanGenerationService service;
    private TradePlanTestFixtures.Environment environment;

    @BeforeEach
    void setUp() {
        marketData = mock(MarketDataClient.class);
        environment = TradePlanTestFixtures.environment();
        service = new OpportunityTradePlanGenerationService(
                environment.opportunities(), environment.contexts(), marketData,
                environment.service(), Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));
        when(marketData.findAllMarkets())
                .thenReturn(List.of(market("BTC/EUR")));
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class)))
                .thenReturn(List.of(freshPrice()));
    }

    private MarketResponse market(String symbol) {
        return new MarketResponse(UUID.randomUUID(), "KRAKEN", symbol, "BTC", "EUR",
                new MarketResponse.MarketStateResponse("OPEN", true, "",
                        TradePlanTestFixtures.NOW));
    }

    private MarketPriceSnapshotResponse freshPrice() {
        return new MarketPriceSnapshotResponse(
                UUID.randomUUID(), "BTC/EUR", null,
                BigDecimal.valueOf(99), BigDecimal.valueOf(101), true,
                TradePlanTestFixtures.NOW, "FRESH",
                UUID.randomUUID().toString(), 7L, TradePlanTestFixtures.NOW);
    }

    private OpportunityTradePlanGenerationService.GenerationResponse generate() {
        var context = TradePlanTestFixtures.context(
                UUID.randomUUID(), 1, environment.owner());
        return service.generate(
                environment.opportunity().id().value(),
                environment.owner(),
                context.tradingAccountId(),
                context);
    }

    @Test
    void generatesProposedPlanForActiveOpportunity() {
        var response = generate();

        var plan = environment.plans()
                .findLatest(new TradePlanId(response.tradePlanId()))
                .orElseThrow();
        assertThat(plan.status()).isEqualTo(TradePlanStatus.PROPOSED);
        assertThat(response.tradePlanVersion()).isEqualTo(plan.version().value());
    }

    @Test
    void selectsAskPriceForLongOpportunity() {
        var response = generate();

        var plan = environment.plans()
                .findLatest(new TradePlanId(response.tradePlanId()))
                .orElseThrow();
        assertThat(plan.execution().entry().price()).isEqualByComparingTo(BigDecimal.valueOf(101));
    }

    @Test
    void rejectsUnknownOrNonActiveOpportunity() {
        assertThatThrownBy(() -> service.generate(
                UUID.randomUUID(), environment.owner(),
                environment.context().tradingAccountId(), environment.context()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> org.assertj.core.api.Assertions.assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("OPPORTUNITY_NOT_ELIGIBLE"));
    }

    @Test
    void rejectsInstrumentWithoutMarketCatalogueEntry() {
        when(marketData.findAllMarkets()).thenReturn(List.of(market("ETH/EUR")));

        assertThatThrownBy(this::generate)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> org.assertj.core.api.Assertions.assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("MARKET_NOT_FOUND"));
    }

    @Test
    void rejectsStaleMarketPrice() {
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class)))
                .thenReturn(List.of(new MarketPriceSnapshotResponse(
                        UUID.randomUUID(), "BTC/EUR", null,
                        BigDecimal.valueOf(99), BigDecimal.valueOf(101), true,
                        TradePlanTestFixtures.NOW.minusSeconds(60), "FRESH",
                        UUID.randomUUID().toString(), 7L, TradePlanTestFixtures.NOW)));

        assertThatThrownBy(this::generate)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> org.assertj.core.api.Assertions.assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("MARKET_PRICE_STALE"));
    }

    @Test
    void rejectsForeignPlanningContext() {
        var context = TradePlanTestFixtures.context(UUID.randomUUID(), 1, environment.owner());
        assertThatThrownBy(() -> service.generate(
                environment.opportunity().id().value(), UUID.randomUUID(),
                context.tradingAccountId(), context))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(x -> org.assertj.core.api.Assertions.assertThat(String.valueOf(((ResponseStatusException) x).getReason()))
                        .contains("PLANNING_CONTEXT_FORBIDDEN"));
    }
}
