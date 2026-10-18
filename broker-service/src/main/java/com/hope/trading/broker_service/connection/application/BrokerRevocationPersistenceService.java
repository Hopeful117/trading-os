package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class BrokerRevocationPersistenceService {
    private final BrokerConnectionRepository connectionRepository;
    private final BrokerRevocationNotificationRepository notificationRepository;
    private final TradingCoreBrokerAccountClient tradingCore;
    private final SecretRevoker secretRevoker;
    private final Clock clock;

    public BrokerRevocationPersistenceService(BrokerConnectionRepository connectionRepository,
                                              BrokerRevocationNotificationRepository notificationRepository,
                                              TradingCoreBrokerAccountClient tradingCore,
                                              SecretRevoker secretRevoker,
                                              Clock clock) {
        this.connectionRepository = connectionRepository;
        this.notificationRepository = notificationRepository;
        this.tradingCore = tradingCore;
        this.secretRevoker = secretRevoker;
        this.clock = clock;
    }

    @Transactional
    public UUID revokeLocally(UUID ownerId, UUID accountId, String authorization) {
        tradingCore.findOwned(accountId, authorization);
        BrokerConnection connection = connectionRepository.findByBrokerAccountIdAndOwnerIdForUpdate(accountId, ownerId)
                .orElseThrow(BrokerConnectionNotFoundException::new);
        Instant now = clock.instant();
        if (connection.activeCredentialReference() != null) {
            secretRevoker.revoke(new CredentialReference(connection.activeCredentialReference()), "USER_REQUEST");
        }
        connection.revoke(now);
        connectionRepository.save(connection);
        return notificationRepository.findByBrokerAccountId(accountId)
                .map(BrokerRevocationNotification::id)
                .orElseGet(() -> notificationRepository.save(
                        BrokerRevocationNotification.pending(accountId, ownerId,
                                connection.externalAccountId(), now)).id());
    }
}
