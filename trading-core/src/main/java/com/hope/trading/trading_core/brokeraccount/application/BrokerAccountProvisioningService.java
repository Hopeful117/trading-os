package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class BrokerAccountProvisioningService {
    private final BrokerAccountRepository brokerAccounts;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final BrokerAccountSnapshotV2Client snapshots;
    private final BrokerSynchronizationService synchronization;
    private final AccountMapper mapper;
    private final Clock clock;
    private final RiskPersistence riskPersistence;

    @Autowired
    public BrokerAccountProvisioningService(BrokerAccountRepository brokerAccounts,
                                             AccountRepository accounts,
                                             UserRepository users,
                                             BrokerAccountSnapshotV2Client snapshots,
                                             BrokerSynchronizationService synchronization,
                                             AccountMapper mapper,
                                             Clock clock,
                                             RiskPersistence riskPersistence) {
        this.brokerAccounts = brokerAccounts;
        this.accounts = accounts;
        this.users = users;
        this.snapshots = snapshots;
        this.synchronization = synchronization;
        this.mapper = mapper;
        this.clock = clock;
        this.riskPersistence = riskPersistence;
    }

    BrokerAccountProvisioningService(BrokerAccountRepository brokerAccounts,
                                     AccountRepository accounts,
                                     UserRepository users,
                                     BrokerAccountSnapshotV2Client snapshots,
                                     BrokerSynchronizationService synchronization,
                                     AccountMapper mapper,
                                     Clock clock) {
        this(brokerAccounts, accounts, users, snapshots, synchronization, mapper, clock, null);
    }

    @Transactional
    public AccountDto provision(UUID ownerId, UUID brokerAccountId) {
        BrokerAccount brokerAccount = brokerAccounts.findByIdAndOwnerIdForUpdate(brokerAccountId, ownerId)
                .orElseGet(() -> importBrokerAccount(ownerId, brokerAccountId));
        if (brokerAccount.connectionStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new IllegalStateException("Broker account must be connected before provisioning");
        }
        if (brokerAccount.externalAccountId() == null || brokerAccount.externalAccountId().isBlank()) {
            throw new IllegalStateException("Broker account has no bound external account");
        }

        Account existing = accounts.findByBrokerAccountId(brokerAccountId).orElse(null);
        if (existing != null) {
            if (existing.getUser() == null || !ownerId.equals(existing.getUser().getUserId())) {
                throw new BrokerAccountOwnershipException();
            }
            return mapper.toDto(existing);
        }

        BrokerAccountSnapshotV2Dto snapshot = snapshots.getAccountSnapshot(brokerAccountId);
        validateSnapshot(snapshot, brokerAccount);
        Account account = createAccount(ownerId, brokerAccount, snapshot);
        accounts.save(account);

        if (riskPersistence == null) {
            throw new IllegalStateException("Risk persistence is required for broker account provisioning");
        }
        if (brokerAccount.riskProfileId() == null || brokerAccount.riskProfileSemanticVersion() == null) {
            throw new IllegalStateException("Broker account has no risk profile reference");
        }
        riskPersistence.profile(brokerAccount.riskProfileId(), brokerAccount.riskProfileSemanticVersion())
                .orElseThrow(() -> new IllegalStateException("Broker account risk profile does not exist"));
        riskPersistence.configuration(account.getAccountId(), brokerAccount.id(),
                "Europe/Paris", account.getBaseCurrency(), account.getAccountId(), "00:00");
        riskPersistence.assignProfile(account.getAccountId(), brokerAccount.riskProfileId(),
                brokerAccount.riskProfileSemanticVersion(), clock.instant(), "broker-account-provisioning");

        synchronization.synchronize(ownerId, brokerAccountId);
        return mapper.toDto(accounts.findByBrokerAccountId(brokerAccountId).orElseThrow());
    }

    private synchronized BrokerAccount importBrokerAccount(UUID ownerId, UUID brokerAccountId) {
        BrokerAccount existing = brokerAccounts.findByIdAndOwnerIdForUpdate(brokerAccountId, ownerId).orElse(null);
        if (existing != null) return existing;
        BrokerAccountSnapshotV2Dto snapshot = snapshots.getAccountSnapshot(brokerAccountId);
        validateSnapshot(snapshot, brokerAccountId);
        BrokerAccount imported = BrokerAccount.importConnected(
                brokerAccountId,
                ownerId,
                snapshot.providerRegistrationReference(),
                com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode.LIVE,
                "Broker account " + snapshot.externalAccountId(),
                snapshot.externalAccountId(),
                snapshot.observedAt());
        return brokerAccounts.save(imported);
    }

    private void validateSnapshot(BrokerAccountSnapshotV2Dto snapshot, UUID brokerAccountId) {
        if (snapshot == null || !brokerAccountId.equals(snapshot.brokerAccountId())) {
            throw new BrokerAccountNotFoundException();
        }
    }

    private void validateSnapshot(BrokerAccountSnapshotV2Dto snapshot, BrokerAccount brokerAccount) {
        if (snapshot == null
                || !brokerAccount.id().equals(snapshot.brokerAccountId())
                || !brokerAccount.externalAccountId().equals(snapshot.externalAccountId())) {
            throw new IllegalStateException("Broker account snapshot identity mismatch");
        }
        if (!"COMPLETE".equals(snapshot.completeness()) || !"FRESH".equals(snapshot.freshness())) {
            throw new BrokerAccountSnapshotIncompleteException(snapshot.completeness(), snapshot.freshness(),
                    snapshot.unavailabilityReasons());
        }
        if (snapshot.account() == null || snapshot.account().valuationAsset() == null
                || snapshot.account().valuationAsset().isBlank()) {
            throw new IllegalStateException("Broker account snapshot has no valuation asset");
        }
        if (snapshot.balances() == null || snapshot.balances().isEmpty()) {
            throw new IllegalStateException("Broker account snapshot has no balances");
        }
    }

    private Account createAccount(UUID ownerId, BrokerAccount brokerAccount,
                                  BrokerAccountSnapshotV2Dto snapshot) {
        BigDecimal equity = snapshot.account().equity() == null
                ? snapshot.account().balance() : snapshot.account().equity();
        Account account = Account.builder()
                .broker(brokerAccount.providerRegistrationReference().toString())
                .brokerAccountId(brokerAccount.id())
                .name(brokerAccount.displayName())
                .baseCurrency(snapshot.account().valuationAsset())
                .equity(equity == null ? BigDecimal.ZERO : equity)
                .peakEquity(equity == null ? BigDecimal.ZERO : equity)
                .startingBalance(equity == null ? BigDecimal.ZERO : equity)
                .marketDataTimestamp(snapshot.observedAt())
                .user(users.getReferenceById(ownerId))
                .build();
        for (Map.Entry<String, BigDecimal> balance : snapshot.balances().entrySet()) {
            AccountBalance value = new AccountBalance();
            value.setAsset(balance.getKey());
            value.setAmount(balance.getValue());
            account.addBalance(value);
        }
        return account;
    }
}
