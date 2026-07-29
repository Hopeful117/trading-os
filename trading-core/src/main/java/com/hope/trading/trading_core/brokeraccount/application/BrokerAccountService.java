package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountResponse;
import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.CredentialReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BrokerAccountService {
    private final BrokerAccountRepository repository;
    private final Clock clock;

    public BrokerAccountResponse create(UUID ownerId, CreateBrokerAccountRequest request) {
        BrokerAccount account = BrokerAccount.create(ownerId, request.provider(), request.displayName(), clock.instant());
        return map(repository.save(account));
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
