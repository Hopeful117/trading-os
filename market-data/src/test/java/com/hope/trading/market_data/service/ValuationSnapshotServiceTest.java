package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest.PriceUse;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.FactStatus;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.PriceObservation;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(ValuationSnapshotServiceTest.TestClockConfiguration.class)
class ValuationSnapshotServiceTest {
    private static final Instant VALUATION_TIME = Instant.parse("2026-08-01T12:00:00Z");

    @Autowired
    private ValuationSnapshotService service;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private PriceObservationRepository observationRepository;
    @Autowired
    private TickerEventPublisher tickerEventPublisher;

    @Test
    void valuesDirectInverseAndSameCurrencyConservatively() {
        observe(market("EUR/USD", "EUR", "USD"), "1.10", "1.11", VALUATION_TIME.minusSeconds(10));
        observe(market("USD/CHF", "USD", "CHF"), "0.89", "0.90", VALUATION_TIME.minusSeconds(10));

        var response = service.create(new ValuationSnapshotBatchRequest(
                "USD", VALUATION_TIME, List.of(), List.of(
                new ValuationSnapshotBatchRequest.Asset("direct", "EUR"),
                new ValuationSnapshotBatchRequest.Asset("inverse", "CHF"),
                new ValuationSnapshotBatchRequest.Asset("same", "USD"))));

        assertThat(response.facts()).extracting(fact -> fact.status())
                .containsExactly(FactStatus.AVAILABLE, FactStatus.AVAILABLE, FactStatus.AVAILABLE);
        assertThat(response.facts().get(0).value()).isEqualByComparingTo("1.10");
        assertThat(response.facts().get(0).conversionLegs().getFirst().source().priceType()).isEqualTo("BID");
        assertThat(response.facts().get(1).value()).isEqualByComparingTo(
                BigDecimal.ONE.divide(new BigDecimal("0.90"), java.math.MathContext.DECIMAL128));
        assertThat(response.facts().get(1).conversionLegs().getFirst().source().priceType())
                .isEqualTo("INVERSE_ASK");
        assertThat(response.facts().get(2).value()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.facts().get(2).conversionLegs().getFirst().source().priceType())
                .isEqualTo("IDENTITY");
    }

    @Test
    void failsClosedForStaleAndFutureOnlyObservations() {
        observe(market("EUR/USD", "EUR", "USD"), "1.10", "1.11", VALUATION_TIME.minusSeconds(301));
        observe(market("GBP/USD", "GBP", "USD"), "1.20", "1.21", VALUATION_TIME.plusSeconds(1));

        var response = service.create(new ValuationSnapshotBatchRequest(
                "USD", VALUATION_TIME, List.of(), List.of(
                new ValuationSnapshotBatchRequest.Asset("stale", "EUR"),
                new ValuationSnapshotBatchRequest.Asset("future", "GBP"))));

        assertThat(response.facts()).extracting(fact -> fact.status())
                .containsExactly(FactStatus.STALE, FactStatus.CONVERSION_UNAVAILABLE);
        assertThat(response.facts()).extracting(fact -> fact.value()).containsOnlyNulls();
        assertThat(response.maxObservationAge()).isEqualTo("PT5M");
        assertThat(response.facts().getFirst().conversionLegs().getFirst().source().observationAge())
                .isEqualTo("PT5M1S");
        assertThat(response.facts().getFirst().conversionLegs().getFirst().source().capturedAt())
                .isEqualTo(VALUATION_TIME.minusSeconds(301));
    }

    @Test
    void excludesLateArrivalsCapturedAfterValuationTime() {
        Market market = market("AUD/USD", "AUD", "USD");
        PriceObservation eligible = observe(
                market, "0.65", "0.66", VALUATION_TIME.minusSeconds(20), VALUATION_TIME.minusSeconds(19));
        observe(market, "0.70", "0.71", VALUATION_TIME.minusSeconds(10), VALUATION_TIME.plusSeconds(1));

        var response = service.create(new ValuationSnapshotBatchRequest(
                "USD", VALUATION_TIME, List.of(),
                List.of(new ValuationSnapshotBatchRequest.Asset("aud", "AUD"))));

        var source = response.facts().getFirst().conversionLegs().getFirst().source();
        assertThat(response.facts().getFirst().value()).isEqualByComparingTo("0.65");
        assertThat(source.observationId()).isEqualTo(eligible.getObservationId());
        assertThat(source.effectiveAt()).isEqualTo(VALUATION_TIME.minusSeconds(20));
        assertThat(source.capturedAt()).isEqualTo(VALUATION_TIME.minusSeconds(19));
        assertThat(source.observationAge()).isEqualTo("PT20S");
    }

    @Test
    void issuesIncreasingVersionsAndPreservesInstrumentProvenance() {
        Market market = market("BTC/USD", "BTC", "USD");
        tickerEventPublisher.publish(new TickerEvent(
                market.getMarketId(), MarketProvider.KRAKEN, market.getSymbol(),
                new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("100"),
                BigDecimal.ONE, VALUATION_TIME.minusSeconds(1)));
        var request = new ValuationSnapshotBatchRequest(
                "USD", VALUATION_TIME,
                List.of(new ValuationSnapshotBatchRequest.Instrument(
                        "btc", market.getMarketId(), PriceUse.CONSERVATIVE_SELL)),
                List.of());

        var first = service.create(request);
        var second = service.create(request);

        assertThat(second.version()).isGreaterThan(first.version());
        var fact = first.facts().getFirst();
        assertThat(fact.value()).isEqualByComparingTo("99");
        assertThat(fact.source().observationId()).isNotNull();
        assertThat(fact.source().marketId()).isEqualTo(market.getMarketId());
        assertThat(fact.source().provider()).isEqualTo("KRAKEN");
        assertThat(fact.source().symbol()).isEqualTo("BTC/USD");
        assertThat(fact.source().priceType()).isEqualTo("BID");
        assertThat(fact.source().effectiveAt()).isEqualTo(VALUATION_TIME.minusSeconds(1));
        assertThat(fact.source().capturedAt()).isEqualTo(VALUATION_TIME);
        assertThat(fact.source().observationAge()).isEqualTo("PT1S");
        assertThat(fact.conversionLegs().getFirst().source().priceType()).isEqualTo("IDENTITY");
        assertThat(first.capturedAt()).isEqualTo(VALUATION_TIME);
        assertThat(first.maxObservationAge()).isEqualTo("PT5M");
        assertThat(first.policyVersion()).isEqualTo("CONSERVATIVE_DIRECT_FX_NO_LOOKAHEAD_V2");
    }

    private Market market(String symbol, String base, String quote) {
        return marketRepository.saveAndFlush(Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol(symbol)
                .baseAsset(base)
                .quoteAsset(quote)
                .marketState(MarketState.builder().tradable(true).build())
                .build());
    }

    private PriceObservation observe(Market market, String bid, String ask, Instant effectiveAt) {
        return observe(market, bid, ask, effectiveAt, effectiveAt);
    }

    private PriceObservation observe(Market market, String bid, String ask,
                                     Instant effectiveAt, Instant capturedAt) {
        return observationRepository.saveAndFlush(new PriceObservation(
                UUID.randomUUID(), market.getMarketId(), market.getProvider(), market.getSymbol(),
                market.getBaseAsset(), market.getQuoteAsset(), new BigDecimal(bid), new BigDecimal(ask),
                new BigDecimal(bid), effectiveAt, capturedAt));
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(VALUATION_TIME, ZoneOffset.UTC);
        }
    }
}
