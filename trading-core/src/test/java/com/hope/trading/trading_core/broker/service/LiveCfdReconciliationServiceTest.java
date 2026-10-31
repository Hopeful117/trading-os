package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.dto.BrokerAccountFactsV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveCfdReconciliationServiceTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-31T12:00:00Z");
    private static final UUID PROVIDER = UUID.randomUUID();
    private final InstrumentFinancialContract contract = new InstrumentFinancialContract(
            "EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
            QuantityUnit.UNDERLYING_UNITS, new BigDecimal("1"), 5, 2,
            new BigDecimal("1"), new BigDecimal("1"),
            new FinancialContractProvenance("market-data", 1, OBSERVED_AT));
    private final InstrumentFinancialContractResolver contracts = mock(InstrumentFinancialContractResolver.class);
    private final LiveCfdReconciliationService service = new LiveCfdReconciliationService(contracts);

    @Test
    void preservesProviderAccountFactsAndMatchesSignedLongPosition() {
        when(contracts.resolveForInstrument("EUR/USD")).thenReturn(Optional.of(contract));

        var result = service.reconcile(snapshot(new BigDecimal("1000")),
                List.of(localTrade("P1", TradeType.BUY, "1000")));

        assertThat(result.status()).isEqualTo(LiveCfdReconciliationService.Status.MATCHED);
        assertThat(result.account().equity()).isEqualByComparingTo("1010");
        assertThat(result.account().usedMargin()).isEqualByComparingTo("100");
        assertThat(result.account().freeMargin()).isEqualByComparingTo("900");
        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.providerPositionReference()).isEqualTo("P1");
            assertThat(position.status()).isEqualTo(LiveCfdReconciliationService.Status.MATCHED);
        });
    }

    @Test
    void preservesNegativeShortQuantityAndClassifiesProviderOnly() {
        when(contracts.resolveForInstrument("EUR/USD")).thenReturn(Optional.of(contract));

        var result = service.reconcile(snapshot(new BigDecimal("-1000")), List.of());

        assertThat(result.status()).isEqualTo(LiveCfdReconciliationService.Status.PROVIDER_ONLY);
        assertThat(result.positions()).singleElement().extracting(
                LiveCfdReconciliationService.PositionResult::status)
                .isEqualTo(LiveCfdReconciliationService.Status.PROVIDER_ONLY);
    }

    @Test
    void distinguishesLocalOnlyAndFinancialMismatch() {
        when(contracts.resolveForInstrument("EUR/USD")).thenReturn(Optional.of(contract));

        var localOnly = service.reconcile(snapshotWithoutPositions(),
                List.of(localTrade("P1", TradeType.BUY, "1000")));
        var mismatch = service.reconcile(snapshot(new BigDecimal("2000")),
                List.of(localTrade("P1", TradeType.BUY, "1000")));

        assertThat(localOnly.status()).isEqualTo(LiveCfdReconciliationService.Status.LOCAL_ONLY);
        assertThat(mismatch.status()).isEqualTo(LiveCfdReconciliationService.Status.MISMATCH);
    }

    @Test
    void failsClosedForIncompleteSnapshotOrMissingCfdContract() {
        var incomplete = service.reconcile(snapshotWithoutAccountFacts(), List.of());
        when(contracts.resolveForInstrument("EUR/USD")).thenReturn(Optional.empty());
        var unclassified = service.reconcile(snapshot(new BigDecimal("1000")), List.of());

        assertThat(incomplete.status()).isEqualTo(LiveCfdReconciliationService.Status.UNAVAILABLE);
        assertThat(unclassified.status()).isEqualTo(LiveCfdReconciliationService.Status.UNAVAILABLE);
    }

    @Test
    void doesNotInterpretIncompletePositionAbsenceAsLocalOnlyClose() {
        var incomplete = new BrokerAccountSnapshotV2Dto(UUID.randomUUID(), PROVIDER, "ctrader-1",
                Map.of(), List.of(), OBSERVED_AT, facts(), List.of(), List.of(),
                "INCOMPLETE", "FRESH", OBSERVED_AT, OBSERVED_AT, List.of("POSITION_COLLECTION_UNAVAILABLE"));

        var result = service.reconcile(incomplete, List.of(localTrade("P1", TradeType.BUY, "1000")));

        assertThat(result.status()).isEqualTo(LiveCfdReconciliationService.Status.UNAVAILABLE);
    }

    @Test
    void failsClosedWhenRequiredSnapshotCollectionIsMissing() {
        var malformed = new BrokerAccountSnapshotV2Dto(UUID.randomUUID(), PROVIDER, "ctrader-1",
                Map.of(), null, OBSERVED_AT, facts(), List.of(), List.of(),
                "COMPLETE", "FRESH", OBSERVED_AT, OBSERVED_AT, List.of());

        var result = service.reconcile(malformed, List.of());

        assertThat(result.status()).isEqualTo(LiveCfdReconciliationService.Status.UNAVAILABLE);
        assertThat(result.reasons()).containsExactly("LIVE_PROVIDER_SNAPSHOT_UNAVAILABLE");
    }

    private BrokerAccountSnapshotV2Dto snapshot(BigDecimal signedQuantity) {
        return new BrokerAccountSnapshotV2Dto(UUID.randomUUID(), PROVIDER, "ctrader-1",
                Map.of("USD", new BigDecimal("1000")),
                List.of(new BrokerPositionSnapshotV2Dto("EUR/USD", signedQuantity, new BigDecimal("1.10000"),
                        OBSERVED_AT, "P1", new BigDecimal("100"), null)), OBSERVED_AT, facts(), List.of(), List.of(),
                "COMPLETE", "FRESH", OBSERVED_AT.minusSeconds(1), OBSERVED_AT, List.of());
    }

    private BrokerAccountSnapshotV2Dto snapshotWithoutPositions() {
        return new BrokerAccountSnapshotV2Dto(UUID.randomUUID(), PROVIDER, "ctrader-1",
                Map.of("USD", new BigDecimal("1000")), List.of(), OBSERVED_AT, facts(), List.of(), List.of(),
                "COMPLETE", "FRESH", OBSERVED_AT.minusSeconds(1), OBSERVED_AT, List.of());
    }

    private BrokerAccountSnapshotV2Dto snapshotWithoutAccountFacts() {
        return new BrokerAccountSnapshotV2Dto(UUID.randomUUID(), PROVIDER, "ctrader-1",
                Map.of(), List.of(), OBSERVED_AT, null, List.of(), List.of(),
                "COMPLETE", "FRESH", OBSERVED_AT, OBSERVED_AT, List.of());
    }

    private BrokerAccountFactsV2Dto facts() {
        return new BrokerAccountFactsV2Dto("USD", new BigDecimal("1000"), new BigDecimal("1010"),
                new BigDecimal("100"), new BigDecimal("900"));
    }

    private Trade localTrade(String reference, TradeType type, String quantity) {
        return Trade.builder().symbol("EUR/USD").productType(InstrumentProductType.CFD)
                .baseCurrency("EUR").quoteCurrency("USD").quantityUnit(QuantityUnit.UNDERLYING_UNITS)
                .contractMultiplier(BigDecimal.ONE).pricePrecision(5).quantityPrecision(2)
                .minimumQuantity(BigDecimal.ONE).quantityIncrement(BigDecimal.ONE)
                .contractSourceId("market-data").contractSourceVersion(1L)
                .contractObservedAt(OBSERVED_AT).type(type).quantity(new BigDecimal(quantity))
                .entryPrice(new BigDecimal("1.10000")).brokerPositionReference(reference)
                .tradeStatus(TradeStatus.OPEN).build();
    }
}
