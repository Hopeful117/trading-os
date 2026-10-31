package com.hope.trading.trading_core.valuation.application;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.market_data.valuation.ReadOnlyMarketValuationPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CfdValuationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final UUID TRADE_ID = UUID.randomUUID();

    @Test
    void valuesLongAndShortPnlUsingConservativeExecutableSide() {
        ReadOnlyMarketValuationPort market = mock(ReadOnlyMarketValuationPort.class);
        when(market.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(snapshot(MarketValuationPort.PriceUse.CONSERVATIVE_SELL, "1.10100", "USD"))
                .thenReturn(snapshot(MarketValuationPort.PriceUse.CONSERVATIVE_BUY, "1.09900", "USD"));
        CfdValuationService service = new CfdValuationService(market, Clock.fixed(NOW, ZoneOffset.UTC));

        var longResult = service.value(trade("1000"), ExecutionMode.PAPER);
        var shortResult = service.value(trade("-1000"), ExecutionMode.PAPER);

        assertThat(longResult.status()).isEqualTo(CfdValuationService.CfdValuationResult.Status.AVAILABLE);
        assertThat(longResult.grossPnlReporting().amount()).isEqualByComparingTo("1.00");
        assertThat(longResult.priceUse()).isEqualTo(MarketValuationPort.PriceUse.CONSERVATIVE_SELL);
        assertThat(shortResult.grossPnlReporting().amount()).isEqualByComparingTo("1.00");
        assertThat(shortResult.priceUse()).isEqualTo(MarketValuationPort.PriceUse.CONSERVATIVE_BUY);
    }

    @Test
    void failsClosedForLiveWithoutCallingMarketData() {
        ReadOnlyMarketValuationPort market = mock(ReadOnlyMarketValuationPort.class);
        CfdValuationService service = new CfdValuationService(market, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.value(trade("1000"), ExecutionMode.LIVE);

        assertThat(result.status()).isEqualTo(CfdValuationService.CfdValuationResult.Status.UNAVAILABLE);
        assertThat(result.reason()).contains("LIVE");
        verifyNoInteractions(market);
    }

    @Test
    void convertsCrossCurrencyPnlWithDirectedFact() {
        ReadOnlyMarketValuationPort market = mock(ReadOnlyMarketValuationPort.class);
        when(market.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(snapshot(MarketValuationPort.PriceUse.CONSERVATIVE_SELL, "1.10100", "EUR"));
        CfdValuationService service = new CfdValuationService(market, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.value(trade("1000", "EUR"), ExecutionMode.PAPER, "EUR");

        assertThat(result.status()).isEqualTo(CfdValuationService.CfdValuationResult.Status.AVAILABLE);
        assertThat(result.grossPnlQuote().amount()).isEqualByComparingTo("1.00");
        assertThat(result.grossPnlReporting().amount()).isEqualByComparingTo("0.90");
        assertThat(result.conversionEffectiveAt()).isEqualTo(NOW.minusSeconds(5));
    }

    @Test
    void rejectsConversionWithWrongDirection() {
        ReadOnlyMarketValuationPort market = mock(ReadOnlyMarketValuationPort.class);
        when(market.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(snapshotWithLeg(MarketValuationPort.PriceUse.CONSERVATIVE_SELL,
                        "1.10100", "EUR", "EUR", "USD"));
        CfdValuationService service = new CfdValuationService(market, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.value(trade("1000", "EUR"), ExecutionMode.PAPER, "EUR");

        assertThat(result.status()).isEqualTo(CfdValuationService.CfdValuationResult.Status.UNAVAILABLE);
        assertThat(result.reason()).contains("conversion");
    }

    @Test
    void composesExplicitMultiHopConversionLegs() {
        ReadOnlyMarketValuationPort market = mock(ReadOnlyMarketValuationPort.class);
        Instant firstAt = NOW.minusSeconds(20);
        Instant secondAt = NOW.minusSeconds(10);
        var markSource = new MarketValuationPort.Fact(
                "INSTRUMENT", TRADE_ID.toString(), UUID.randomUUID(), null,
                MarketValuationPort.PriceUse.CONSERVATIVE_SELL, new BigDecimal("0.79272"),
                new BigDecimal("1.10100"), new BigDecimal("0.72"), "AVAILABLE", "mark-source",
                NOW.minusSeconds(5), NOW.minusSeconds(4), secondAt, NOW.minusSeconds(9),
                List.of(new MarketValuationPort.ConversionLeg("USD", "CHF", new BigDecimal("0.90"),
                                "BID", "fx-1", firstAt, firstAt.plusSeconds(1)),
                        new MarketValuationPort.ConversionLeg("CHF", "EUR", new BigDecimal("0.80"),
                                "BID", "fx-2", secondAt, secondAt.plusSeconds(1))));
        when(market.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "EUR", NOW, NOW,
                        "policy-v1", "PT5M", true, List.of(markSource), "facts", List.of()));
        CfdValuationService service = new CfdValuationService(market, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.value(trade("1000", "EUR"), ExecutionMode.PAPER, "EUR");

        assertThat(result.status()).isEqualTo(CfdValuationService.CfdValuationResult.Status.AVAILABLE);
        assertThat(result.grossPnlReporting().amount()).isEqualByComparingTo("0.72");
        assertThat(result.conversionLegs()).hasSize(2);
    }

    private Trade trade(String quantity) {
        return trade(quantity, "USD");
    }

    private Trade trade(String quantity, String reportingCurrency) {
        Account account = Account.builder().baseCurrency(reportingCurrency).name("paper").build();
        Trade trade = Trade.builder()
                .tradeId(TRADE_ID)
                .symbol("EUR/USD")
                .type(TradeType.BUY)
                .entryPrice(new BigDecimal("1.10000"))
                .quantity(new BigDecimal(quantity))
                .tradeStatus(TradeStatus.OPEN)
                .account(account)
                .productType(InstrumentProductType.CFD)
                .baseCurrency("EUR")
                .quoteCurrency("USD")
                .quantityUnit(QuantityUnit.UNDERLYING_UNITS)
                .contractMultiplier(BigDecimal.ONE)
                .pricePrecision(5)
                .quantityPrecision(0)
                .minimumQuantity(new BigDecimal("1000"))
                .quantityIncrement(new BigDecimal("1000"))
                .contractSourceId("test:eurusd")
                .contractSourceVersion(1L)
                .contractObservedAt(NOW.minusSeconds(10))
                .build();
        return trade;
    }

    private MarketValuationPort.Snapshot snapshot(MarketValuationPort.PriceUse priceUse,
                                                  String mark, String reportingCurrency) {
        return snapshotWithLeg(priceUse, mark, reportingCurrency, "USD", "EUR");
    }

    private MarketValuationPort.Snapshot snapshotWithLeg(MarketValuationPort.PriceUse priceUse,
                                                         String mark, String reportingCurrency,
                                                         String fromCurrency, String toCurrency) {
        Instant markAt = NOW.minusSeconds(10);
        Instant conversionAt = NOW.minusSeconds(5);
        List<MarketValuationPort.ConversionLeg> legs = reportingCurrency.equals("USD")
                ? List.of()
                : List.of(new MarketValuationPort.ConversionLeg(fromCurrency, toCurrency,
                new BigDecimal("0.90"), "BID", "fx-source", conversionAt, NOW.minusSeconds(4)));
        var source = new MarketValuationPort.Fact(
                "INSTRUMENT", TRADE_ID.toString(), UUID.randomUUID(), null, priceUse,
                reportingCurrency.equals("USD") ? new BigDecimal(mark) : new BigDecimal(mark).multiply(new BigDecimal("0.90")),
                new BigDecimal(mark), reportingCurrency.equals("USD") ? null : new BigDecimal("0.90"),
                "AVAILABLE", "market-source", markAt, NOW.minusSeconds(9), conversionAt, NOW.minusSeconds(4), legs);
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, reportingCurrency, NOW, NOW,
                "policy-v1", "PT5M", true, List.of(source), "facts", List.of());
    }
}
