package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerAccountProvisioningServiceTest {
    @Test
    void importsConnectedCTraderDemoAccountAsTradingOsLive() {
        record CTraderProviderFixture(String externalAccountId, String environment, boolean isLive) {
            BrokerAccountSnapshotV2Dto snapshot(UUID brokerAccountId, UUID providerReference, Instant observedAt) {
                // Provider environment facts stay outside the provider-neutral Trading Core DTO.
                return new BrokerAccountSnapshotV2Dto(
                        brokerAccountId, providerReference, externalAccountId,
                        Map.of("USD", new BigDecimal("10000")), java.util.List.of(), observedAt);
            }
        }

        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerAccountSnapshotV2Client snapshots = mock(BrokerAccountSnapshotV2Client.class);
        BrokerSynchronizationService synchronization = mock(BrokerSynchronizationService.class);
        UUID ownerId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-10-27T04:00:00Z");
        CTraderProviderFixture providerFixture = new CTraderProviderFixture(
                "FTMO-CTRADER-DEMO-49289512", "DEMO", false);
        BrokerAccountSnapshotV2Dto snapshot = providerFixture.snapshot(
                brokerAccountId, providerReference, observedAt);

        when(brokerAccounts.findByIdAndOwnerIdForUpdate(brokerAccountId, ownerId)).thenReturn(Optional.empty());
        when(snapshots.getAccountSnapshot(brokerAccountId)).thenReturn(snapshot);
        when(brokerAccounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Account account = mock(Account.class);
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(ownerId);
        when(account.getUser()).thenReturn(user);
        when(accounts.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(account));

        BrokerAccountProvisioningService service = new BrokerAccountProvisioningService(
                brokerAccounts, accounts, mock(UserRepository.class), snapshots, synchronization,
                mock(AccountMapper.class), Clock.fixed(observedAt, ZoneOffset.UTC));

        AccountDto result = service.provision(ownerId, brokerAccountId);

        assertThat(result).isNull();
        assertThat(providerFixture.environment()).isEqualTo("DEMO");
        assertThat(providerFixture.isLive()).isFalse();
        verify(brokerAccounts).save(argThat(value ->
                value.executionMode() == com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode.LIVE
                        && value.externalAccountId().equals(providerFixture.externalAccountId())));
    }
}
