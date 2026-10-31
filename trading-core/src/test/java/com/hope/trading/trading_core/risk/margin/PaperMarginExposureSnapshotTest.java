package com.hope.trading.trading_core.risk.margin;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperMarginExposureSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void preservesPersistedSignedQuantityAndCreatesDeterministicSnapshot() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).build();
        Trade trade = Trade.builder().tradeId(UUID.randomUUID()).version(3).account(account)
                .symbol("EUR/USD").type(TradeType.SELL).quantity(new BigDecimal("-1000"))
                .productType(InstrumentProductType.CFD).quoteCurrency("USD").baseCurrency("EUR")
                .quantityUnit(QuantityUnit.UNDERLYING_UNITS).contractMultiplier(BigDecimal.ONE)
                .contractSourceId("market").contractSourceVersion(1L).openedAt(NOW.minusSeconds(1))
                .tradeStatus(TradeStatus.OPEN).build();
        PaperMarginExposureSnapshot snapshot = new PaperMarginExposureSnapshot();

        var first = snapshot.resolve(accountId, "EUR/USD", List.of(trade), NOW);
        var second = snapshot.resolve(accountId, "EUR/USD", List.of(trade), NOW);

        assertThat(first.available()).isTrue();
        assertThat(first.signedQuantity()).isEqualByComparingTo("-1000");
        assertThat(first.snapshotId()).isEqualTo(second.snapshotId());
    }

    @Test
    void snapshotIdentityDoesNotDependOnTradeOrder() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).build();
        Trade first = trade(account, TradeType.BUY, "1000");
        Trade second = trade(account, TradeType.BUY, "500");
        var snapshot = new PaperMarginExposureSnapshot();

        assertThat(snapshot.resolve(accountId, "EUR/USD", List.of(first, second), NOW).snapshotId())
                .isEqualTo(snapshot.resolve(accountId, "EUR/USD", List.of(second, first), NOW).snapshotId());
    }

    @Test
    void rejectsOppositeOpenTradesAsUnsupportedHedging() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).build();
        Trade longTrade = trade(account, TradeType.BUY, "1000");
        Trade shortTrade = trade(account, TradeType.SELL, "-500");

        var result = new PaperMarginExposureSnapshot().resolve(accountId, "EUR/USD",
                List.of(longTrade, shortTrade), NOW);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo("HEDGING_UNSUPPORTED");
    }

    private Trade trade(Account account, TradeType type, String quantity) {
        return Trade.builder().tradeId(UUID.randomUUID()).account(account).symbol("EUR/USD").type(type)
                .quantity(new BigDecimal(quantity)).productType(InstrumentProductType.CFD)
                .quoteCurrency("USD").baseCurrency("EUR").quantityUnit(QuantityUnit.UNDERLYING_UNITS)
                .contractMultiplier(BigDecimal.ONE).contractSourceId("market").contractSourceVersion(1L)
                .openedAt(NOW.minusSeconds(1)).tradeStatus(TradeStatus.OPEN).build();
    }
}
