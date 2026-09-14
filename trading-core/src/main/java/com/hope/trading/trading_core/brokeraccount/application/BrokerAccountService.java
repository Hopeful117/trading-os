package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountResponse;
import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.CredentialReference;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BrokerAccountService {
    private final BrokerAccountRepository repository;
    private final AccountRepository accountRepository;
    private final RulesRepository rulesRepository;
    private final AccountMapper accountMapper;
    private final Clock clock;

    public BrokerAccountResponse create(UUID ownerId, CreateBrokerAccountRequest request) {
        BrokerAccount account = BrokerAccount.create(
                ownerId,
                request.provider(),
                request.executionMode(),
                request.displayName(),
                clock.instant()
        );

        BrokerAccount savedAccount = repository.save(account);

        // For PAPER accounts, create associated Account with initial capital
        if (request.executionMode() == ExecutionMode.PAPER) {
            BigDecimal initialCapital = request.initialCapital();
            if (initialCapital == null || initialCapital.signum() <= 0) {
                throw new IllegalArgumentException("initialCapital must be positive for PAPER accounts");
            }
            createPaperAccount(ownerId, savedAccount, request.initialCapital());
        }

        return map(savedAccount);
    }

    private void createPaperAccount(UUID ownerId, BrokerAccount brokerAccount, BigDecimal initialCapital) {
        // Get default rules (or create default if none exists)
        Rules rules = rulesRepository.findByName("Default Paper Trading Rules")
                .orElseGet(() -> createDefaultRules());

        Account account = Account.builder()
                .broker(brokerAccount.provider().name())
                .name(brokerAccount.displayName())
                .baseCurrency("USD") // PAPER accounts use USD as default base currency
                .rules(rules)
                .equity(initialCapital)
                .peakEquity(initialCapital)
                .build();

        // Add initial capital as balance
        AccountBalance balance = new AccountBalance();
        balance.setAsset("USD");
        balance.setAmount(initialCapital);
        account.addBalance(balance);

        // Link the account to the user and broker account
        // Note: The user relationship is set via the owner in the service layer
        accountRepository.save(account);
    }

    private Rules createDefaultRules() {
        Rules rules = Rules.builder()
                .name("Default Paper Trading Rules")
                .active(true)
                .maxRiskPerTrade(new java.math.BigDecimal("0.02"))
                .maxDailyLoss(new java.math.BigDecimal("0.05"))
                .maxTotalDrawdown(new java.math.BigDecimal("0.10"))
                .maxTradesPerDay(100)
                .cooldownMinutesBetweenTrades(0)
                .maxLeverage(new java.math.BigDecimal("1"))
                .allowedSessions("24/7")
                .build();
        return rulesRepository.save(rules);
    }

    @Transactional(readOnly = true)
    public BrokerAccountResponse get(UUID ownerId, UUID accountId) {
        return map(requireOwned(ownerId, accountId));
    }

    @Transactional(readOnly = true)
    public List<BrokerAccountResponse> list(UUID ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::map).toList();
    }

    public BrokerAccountResponse disconnect(UUID ownerId, UUID accountId) {
        BrokerAccount account = requireOwned(ownerId, accountId);
        account.disconnect(clock.instant());
        return map(account);
    }

    public void revoke(UUID ownerId, UUID accountId) {
        requireOwned(ownerId, accountId).markRevoked(clock.instant());
    }

    public BrokerAccountResponse updateStatus(UUID ownerId, UUID accountId, BrokerConnectionStatus status,
                                              UUID credentialReference, String externalAccountId,
                                              Instant validatedAt) {
        BrokerAccount account = requireOwned(ownerId, accountId);
        // PAPER accounts don't need external broker connection status updates
        if (account.executionMode() == ExecutionMode.PAPER) {
            throw new IllegalStateException("Cannot update connection status for PAPER accounts");
        }
        switch (status) {
            case PENDING_VALIDATION -> {
                if (account.connectionStatus() != BrokerConnectionStatus.PENDING_VALIDATION) {
                    account.markPendingValidation(clock.instant());
                }
            }
            case CONNECTED -> {
                ensurePending(account);
                account.markConnected(new CredentialReference(credentialReference), externalAccountId, validatedAt);
            }
            case INVALID_CREDENTIALS -> {
                ensurePending(account);
                account.markInvalidCredentials(validatedAt);
            }
            case INSUFFICIENT_PERMISSIONS -> {
                ensurePending(account);
                account.markInsufficientPermissions(validatedAt);
            }
            case TEMPORARILY_UNAVAILABLE -> {
                ensurePending(account);
                account.markTemporarilyUnavailable(validatedAt);
            }
            case DISCONNECTED -> account.disconnect(validatedAt);
            case REVOKED -> account.markRevoked(validatedAt);
            default -> throw new IllegalArgumentException("Unsupported technical status callback");
        }
        return map(account);
    }

    private void ensurePending(BrokerAccount account) {
        if (account.connectionStatus() != BrokerConnectionStatus.PENDING_VALIDATION) {
            account.markPendingValidation(clock.instant());
        }
    }

    private BrokerAccount requireOwned(UUID ownerId, UUID accountId) {
        if (!repository.existsById(accountId)) {
            throw new BrokerAccountNotFoundException();
        }
        return repository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(BrokerAccountOwnershipException::new);
    }

    private BrokerAccountResponse map(BrokerAccount account) {
        return new BrokerAccountResponse(account.id(), account.provider(), account.displayName(),
                account.externalAccountId(), account.connectionStatus(), account.lastValidatedAt(),
                account.lastSynchronizedAt(), account.createdAt(), account.updatedAt());
    }
}