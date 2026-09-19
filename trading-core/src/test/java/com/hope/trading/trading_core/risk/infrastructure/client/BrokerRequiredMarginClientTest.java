package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrokerRequiredMarginClientTest {
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Test
    void rejectsStaleTechnicalFactsBeforeRequestingMargin() {
        BrokerAccount broker = BrokerAccount.create(UUID.randomUUID(), BrokerProvider.KRAKEN,
                ExecutionMode.LIVE, "live", NOW);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerMarginFeignClient margin = mock(BrokerMarginFeignClient.class);
        BrokerTechnicalCapabilitiesFeignClient capabilities = mock(BrokerTechnicalCapabilitiesFeignClient.class);
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(capabilities.get(broker.id(), "BTC/USD")).thenReturn(new BrokerTechnicalCapabilities(
                broker.id(), "KRAKEN", "BTC/USD", 1, NOW.minus(Duration.ofMinutes(6)),
                List.of("MARKET"), List.of(BigDecimal.ONE)));

        BrokerRequiredMarginClient client = new BrokerRequiredMarginClient(margin, capabilities, brokers, accounts,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThat(client.resolve(new RequiredMarginPort.Request(broker.id(), "BTC/USD", "LONG",
                BigDecimal.ONE, new BigDecimal("100"), NOW))).isEmpty();
        verifyNoInteractions(margin);
    }

    @Test
    void derivesPaperMarginLocallyWithoutCallingBrokerService() {
        UUID owner = UUID.randomUUID();
        BrokerAccount broker = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.PAPER, "paper", NOW);
        var account = com.hope.trading.trading_core.model.Account.builder().accountId(UUID.randomUUID())
                .brokerAccountId(broker.id()).baseCurrency("USD").equity(new BigDecimal("1000")).build();
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerMarginFeignClient margin = mock(BrokerMarginFeignClient.class);
        BrokerTechnicalCapabilitiesFeignClient capabilities = mock(BrokerTechnicalCapabilitiesFeignClient.class);
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(accounts.findByBrokerAccountId(broker.id())).thenReturn(Optional.of(account));

        BrokerRequiredMarginClient client = new BrokerRequiredMarginClient(margin, capabilities, brokers, accounts,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThat(client.resolve(new RequiredMarginPort.Request(broker.id(), "BTC/USD", "LONG",
                new BigDecimal("2"), new BigDecimal("100"), NOW))).get()
                .satisfies(fact -> assertThat(fact.amount()).isEqualByComparingTo("200"));
        verifyNoInteractions(margin, capabilities);
    }
}
