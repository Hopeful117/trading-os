package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dto.Position;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3C: protects the broker→local account synchronization —
 * balances are replaced from the broker snapshot, open positions mirror the
 * broker state, equity derives from base-currency balance plus unrealized
 * PnL, and unknown users fail explicitly.
 */
class BrokerSynchronizationServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final BrokerAccountRepository brokerAccountRepository = mock(BrokerAccountRepository.class);
    private final TradeRepository tradeRepository = mock(TradeRepository.class);
    private final BrokerApiClient brokerApiClient = mock(BrokerApiClient.class);
    private final BrokerAccountSnapshotV2Client brokerAccountSnapshotV2Client = mock(BrokerAccountSnapshotV2Client.class);
    private final AccountMapper accountMapper = new AccountMapper();

    private BrokerSynchronizationServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final String username = "trader";
    private User user;

    @BeforeEach
    void setUp() {
        service = new BrokerSynchronizationServiceImpl(
                userRepository, accountRepository, tradeRepository,
                new TradingCalculatorServiceImpl(), brokerApiClient, brokerAccountSnapshotV2Client,
                accountMapper, brokerAccountRepository);

        user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private BrokerAccountDto snapshot(Map<String, BigDecimal> balances,
                                      Position... positions) {
        AccountBalanceDto balanceDto = AccountBalanceDto.builder()
                .balances(balances)
                .build();
        return BrokerAccountDto.builder()
                .broker(BrokerProvider.KRAKEN.toString())
                .accountName("main")
                .baseCurrency("USD")
                .balances(balanceDto)
                .openTrades(List.of(positions))
                .build();
    }

    @Test
    void unknownUserFailsExplicitly() {
        assertThatThrownBy(() -> service.synchronize("ghost"))
                .isInstanceOf(com.hope.trading.trading_core.exception
                        .EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void synchronizationMirrorsBrokerSnapshotIntoLocalState() {
        Position longPosition = Position.builder()
                .brokerPositionId("pos-1").symbol("XBT/USD").side("buy")
                .quantity(new BigDecimal("2")).entryPrice(new BigDecimal("100"))
                .build();
        BrokerAccountDto snapshot = snapshot(
                Map.of("USD", new BigDecimal("5000")), longPosition);

        when(brokerApiClient.getAccount()).thenReturn(snapshot);
        service.synchronize(username);

        Account account = user.getAccounts().getFirst();
        assertThat(account.getBroker()).isEqualTo(BrokerProvider.KRAKEN.toString());
        // Base-currency balance + unrealized PnL of the open position.
        // Open positions without realized PnL contribute zero to equity.
        assertThat(account.getEquity()).isEqualByComparingTo("5000");
        assertThat(account.getPeakEquity()).isEqualByComparingTo("5000");
        assertThat(account.getBalances()).anySatisfy(balance ->
                assertThat(balance.getAsset()).isEqualTo("USD"));
        assertThat(account.getTrades()).hasSize(1);
        Trade synced = account.getTrades().getFirst();
        assertThat(synced.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        assertThat(synced.getType()).isEqualTo(TradeType.BUY);
        assertThat(synced.getEntryPrice()).isEqualByComparingTo("100");
    }

    @Test
    void repeatedSynchronizationReplacesOpenPositionsInsteadOfDuplicating() {
        Position positionV1 = Position.builder()
                .brokerPositionId("pos-1").symbol("XBT/USD").side("buy")
                .quantity(new BigDecimal("2")).entryPrice(new BigDecimal("100"))
                .build();
        BrokerAccountDto first = snapshot(Map.of("USD", new BigDecimal("5000")),
                positionV1);
        Mockito.when(brokerApiClient.getAccount()).thenReturn(first);
        service.synchronize(username);

        Account synchronizedAccount = user.getAccounts().getFirst();
        Mockito.when(accountRepository.findByUser_UserIdAndBroker(
                        userId, BrokerProvider.KRAKEN.toString()))
                .thenReturn(Optional.of(synchronizedAccount));

        Position positionV2 = Position.builder()
                .brokerPositionId("pos-2").symbol("ETH/USD").side("sell")
                .quantity(new BigDecimal("5")).entryPrice(new BigDecimal("3000"))
                .build();
        BrokerAccountDto second = snapshot(Map.of("USD", new BigDecimal("6000")),
                positionV2);
        Mockito.when(brokerApiClient.getAccount()).thenReturn(second);
        service.synchronize(username);

        Account account = user.getAccounts().getFirst();
        assertThat(account.getTrades()).hasSize(1);
        assertThat(account.getTrades().getFirst().getSymbol()).isEqualTo("ETH/USD");
        // Open positions without realized PnL contribute zero to equity.
        assertThat(account.getEquity()).isEqualByComparingTo("6000");
    }

    @Test
    void v2SynchronizationUsesExplicitBrokerAccountRelation() {
        UUID ownerId = UUID.randomUUID();
        BrokerAccount brokerAccount = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN,
                ExecutionMode.LIVE, "Kraken live", java.time.Instant.now());
        User owner = new User();
        owner.setUserId(ownerId);
        Account account = Account.builder()
                .brokerAccountId(brokerAccount.id())
                .broker(BrokerProvider.KRAKEN.toString())
                .name("Kraken live")
                .baseCurrency("USD")
                .user(owner)
                .build();
        when(accountRepository.findByBrokerAccountId(brokerAccount.id())).thenReturn(Optional.of(account));
        when(brokerAccountRepository.findByIdAndOwnerId(brokerAccount.id(), ownerId))
                .thenReturn(Optional.of(brokerAccount));
        when(brokerAccountSnapshotV2Client.getAccountSnapshot(brokerAccount.id())).thenReturn(
                new BrokerAccountSnapshotV2Dto(
                        brokerAccount.id(), BrokerProvider.KRAKEN, "external-1",
                        Map.of("USD", new BigDecimal("6000")),
                        List.of(new BrokerPositionSnapshotV2Dto(
                                "XBT/USD", new BigDecimal("2"), new BigDecimal("100"),
                                java.time.Instant.now(), "position-1")),
                        java.time.Instant.now()));

        service.synchronize(ownerId, brokerAccount.id());

        assertThat(account.getBrokerAccountId()).isEqualTo(brokerAccount.id());
        assertThat(account.getTrades()).hasSize(1);
        assertThat(account.getTrades().getFirst().getQuantity()).isEqualByComparingTo("2");
        verify(accountRepository, never()).findByUser_UserIdAndBroker(ownerId, "KRAKEN");
    }

    @Test
    void v2SynchronizationRejectsCrossOwnerMapping() {
        UUID requestedOwnerId = UUID.randomUUID();
        User otherOwner = new User();
        otherOwner.setUserId(UUID.randomUUID());
        Account account = Account.builder()
                .brokerAccountId(UUID.randomUUID())
                .broker(BrokerProvider.KRAKEN.toString())
                .name("Kraken live")
                .baseCurrency("USD")
                .user(otherOwner)
                .build();
        when(accountRepository.findByBrokerAccountId(account.getBrokerAccountId()))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.synchronize(requestedOwnerId, account.getBrokerAccountId()))
                .isInstanceOf(com.hope.trading.trading_core.exception.EntityNotFoundException.class)
                .hasMessage("Account mapping not found");
        verify(brokerAccountRepository, never()).findByIdAndOwnerId(
                account.getBrokerAccountId(), requestedOwnerId);
    }

    @Test
    void v2SynchronizationRejectsProviderMismatch() {
        UUID ownerId = UUID.randomUUID();
        BrokerAccount brokerAccount = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN,
                ExecutionMode.LIVE, "Kraken live", java.time.Instant.now());
        User owner = new User();
        owner.setUserId(ownerId);
        Account account = Account.builder()
                .brokerAccountId(brokerAccount.id())
                .broker(BrokerProvider.KRAKEN.toString())
                .name("Kraken live")
                .baseCurrency("USD")
                .user(owner)
                .build();
        when(accountRepository.findByBrokerAccountId(brokerAccount.id())).thenReturn(Optional.of(account));
        when(brokerAccountRepository.findByIdAndOwnerId(brokerAccount.id(), ownerId))
                .thenReturn(Optional.of(brokerAccount));
        when(brokerAccountSnapshotV2Client.getAccountSnapshot(brokerAccount.id())).thenReturn(
                new BrokerAccountSnapshotV2Dto(
                        brokerAccount.id(), UUID.randomUUID(), "external-1", Map.of(), List.of(),
                        java.time.Instant.now()));

        assertThatThrownBy(() -> service.synchronize(ownerId, brokerAccount.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Broker account snapshot provider mismatch");
    }

    @Test
    void v2SynchronizationRejectsExternalAccountMismatch() {
        UUID ownerId = UUID.randomUUID();
        BrokerAccount brokerAccount = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN,
                ExecutionMode.LIVE, "Kraken live", java.time.Instant.now());
        brokerAccount.markPendingValidation(java.time.Instant.now());
        brokerAccount.markConnected(
                new com.hope.trading.trading_core.brokeraccount.domain.CredentialReference(UUID.randomUUID()),
                "external-1", java.time.Instant.now());
        User owner = new User();
        owner.setUserId(ownerId);
        Account account = Account.builder()
                .brokerAccountId(brokerAccount.id())
                .broker(BrokerProvider.KRAKEN.toString())
                .name("Kraken live")
                .baseCurrency("USD")
                .user(owner)
                .build();
        when(accountRepository.findByBrokerAccountId(brokerAccount.id())).thenReturn(Optional.of(account));
        when(brokerAccountRepository.findByIdAndOwnerId(brokerAccount.id(), ownerId))
                .thenReturn(Optional.of(brokerAccount));
        when(brokerAccountSnapshotV2Client.getAccountSnapshot(brokerAccount.id())).thenReturn(
                new BrokerAccountSnapshotV2Dto(
                        brokerAccount.id(), BrokerProvider.KRAKEN, "external-2", Map.of(), List.of(),
                        java.time.Instant.now()));

        assertThatThrownBy(() -> service.synchronize(ownerId, brokerAccount.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("External broker account identity mismatch");
    }
}
