package com.hope.trading.trading_core.risk.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.service.AccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrokerCapabilityQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    void paperCapabilitiesUseProviderNeutralFactsAfterTradingCoreOwnershipCheck() {
        AccountService accounts = mock(AccountService.class);
        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        BrokerTechnicalCapabilitiesFeignClient capabilities = mock(BrokerTechnicalCapabilitiesFeignClient.class);
        UUID accountId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BrokerAccount broker = mock(BrokerAccount.class);
        when(accounts.getAccountDtoById(accountId, "trader")).thenReturn(AccountDto.builder()
                .accountId(accountId).brokerAccountId(brokerId).userId(ownerId).build());
        when(brokerAccounts.findByIdAndOwnerId(brokerId, ownerId)).thenReturn(Optional.of(broker));
        when(broker.provider()).thenReturn(BrokerProvider.KRAKEN);
        when(broker.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(capabilities.getByProvider("KRAKEN", brokerId, "BTC/USD")).thenReturn(
                new BrokerTechnicalCapabilities(brokerId, "KRAKEN", "BTC/USD", 4, NOW,
                        List.of("MARKET"), List.of(BigDecimal.ONE), List.of(BigDecimal.ONE)));

        BrokerCapabilityQueryService service = new BrokerCapabilityQueryService(accounts, brokerAccounts,
                capabilities, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        var result = service.resolve(accountId, "trader", List.of(" btc/usd "));

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.instrument()).isEqualTo("BTC/USD");
            assertThat(value.available()).isTrue();
            assertThat(value.supportedOrderTypes()).containsExactly("MARKET");
        });
        verify(brokerAccounts).findByIdAndOwnerId(brokerId, ownerId);
    }

    @Test
    void staleCapabilityFailsClosed() {
        AccountService accounts = mock(AccountService.class);
        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        BrokerTechnicalCapabilitiesFeignClient capabilities = mock(BrokerTechnicalCapabilitiesFeignClient.class);
        UUID accountId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BrokerAccount broker = mock(BrokerAccount.class);
        when(accounts.getAccountDtoById(accountId, "trader")).thenReturn(AccountDto.builder()
                .accountId(accountId).brokerAccountId(brokerId).userId(ownerId).build());
        when(brokerAccounts.findByIdAndOwnerId(brokerId, ownerId)).thenReturn(Optional.of(broker));
        when(broker.provider()).thenReturn(BrokerProvider.KRAKEN);
        when(broker.executionMode()).thenReturn(ExecutionMode.LIVE);
        when(capabilities.get(brokerId, "BTC/USD")).thenReturn(
                new BrokerTechnicalCapabilities(brokerId, "KRAKEN", "BTC/USD", 4,
                        NOW.minus(Duration.ofMinutes(6)), List.of("MARKET"),
                        List.of(BigDecimal.ONE), List.of(BigDecimal.ONE)));

        var result = new BrokerCapabilityQueryService(accounts, brokerAccounts, capabilities,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5))
                .resolve(accountId, "trader", List.of("BTC/USD"));

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.available()).isFalse();
            assertThat(value.reasons()).containsExactly("BROKER_CAPABILITY_UNAVAILABLE");
        });
    }

    @Test
    void foreignAccountCannotResolveCapabilitiesThroughAnotherUsername() {
        AccountService accounts = mock(AccountService.class);
        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        BrokerTechnicalCapabilitiesFeignClient capabilities = mock(BrokerTechnicalCapabilitiesFeignClient.class);
        UUID accountId = UUID.randomUUID();
        when(accounts.getAccountDtoById(accountId, "attacker"))
                .thenThrow(new EntityNotFoundException("account not found"));

        var service = new BrokerCapabilityQueryService(accounts, brokerAccounts, capabilities,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.resolve(accountId, "attacker", List.of("BTC/USD")))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(brokerAccounts, capabilities);
    }
}
