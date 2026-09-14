package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModeAwareRiskFactsProviderTest {
    private final BrokerRiskFactsPort liveFacts = mock(BrokerRiskFactsPort.class);
    private final BrokerAccount brokerAccount = mock(BrokerAccount.class);
    private final Instant from = Instant.parse("2026-08-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void liveUsesBrokerFactsAndDoesNotReadLocalState() {
        UUID brokerId = UUID.randomUUID();
        Account account = mock(Account.class);
        BrokerRiskFactsPort.Snapshot brokerSnapshot = new BrokerRiskFactsPort.Snapshot(
                brokerId, 1, from.plusSeconds(1), true, List.of(), Map.of(),
                new BrokerRiskFactsPort.Account("USD", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO),
                List.of(), List.of(), List.of(), "{}");
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(liveFacts.load(brokerId, from, to)).thenReturn(brokerSnapshot);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());

        assertThat(provider.load(account, brokerAccount, brokerId, from, to).sourceId()).isEqualTo(brokerId);
        verify(liveFacts).load(brokerId, from, to);
        verifyNoInteractions(account);
    }

    @Test
    void paperUsesLocalBalancesAndOpenTradesWithoutCallingBrokerFacts() {
        UUID brokerId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .equity(new BigDecimal("1000")).balances(List.of(
                        AccountBalance.builder().asset("USD").amount(new BigDecimal("1000")).build()))
                .trades(List.of(Trade.builder().tradeId(tradeId).symbol("BTC/USD").type(TradeType.BUY)
                        .entryPrice(new BigDecimal("100")).quantity(BigDecimal.ONE)
                        .tradeStatus(TradeStatus.OPEN).build()))
                .build();
        when(brokerAccount.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccount.id()).thenReturn(brokerId);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());

        RiskFactsProvider.Snapshot snapshot = provider.load(account, brokerAccount, brokerId, from, to);

        assertThat(snapshot.assetBalances()).containsEntry("USD", new BigDecimal("1000"));
        assertThat(snapshot.positions()).singleElement().extracting(RiskFactsProvider.Position::positionId)
                .isEqualTo(tradeId);
        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.unavailabilityReasons()).contains("PAPER_MARGIN_UNAVAILABLE");
        verifyNoInteractions(liveFacts);
    }

    @Test
    void unavailableExecutionModeDoesNotFallBackToPaper() {
        when(brokerAccount.executionMode()).thenReturn(null);
        ModeAwareRiskFactsProvider provider = new ModeAwareRiskFactsProvider(liveFacts, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.load(
                mock(Account.class), brokerAccount, UUID.randomUUID(), from, to))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(liveFacts);
    }
}
