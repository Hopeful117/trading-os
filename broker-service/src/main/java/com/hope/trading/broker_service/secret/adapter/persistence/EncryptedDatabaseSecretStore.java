package com.hope.trading.broker_service.secret.adapter.persistence;

import com.hope.trading.broker_service.secret.application.ConcurrentCredentialRotationException;
import com.hope.trading.broker_service.secret.application.SecretCipher;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.application.SecretRevokedException;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import com.hope.trading.broker_service.secret.application.SecretRotator;
import com.hope.trading.broker_service.secret.application.SecretWriter;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.NewSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import com.hope.trading.broker_service.secret.domain.SecretMetadata;
import com.hope.trading.broker_service.secret.domain.SecretStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
public class EncryptedDatabaseSecretStore implements SecretWriter, SecretReader, SecretRotator, SecretRevoker {
    private final BrokerSecretRepository repository;
    private final SecretCipher cipher;
    private final Clock clock;

    @Override
    @Transactional
    public CredentialReference write(NewSecret secret, SecretMetadata metadata) {
        if (repository.findByBrokerAccountIdAndStatus(metadata.brokerAccountId(), SecretStatus.ACTIVE).isPresent()) {
            throw new ConcurrentCredentialRotationException();
        }
        return createAndActivate(secret, metadata, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public PlainSecret read(CredentialReference reference) {
        BrokerSecretEntity entity = repository.findById(reference.value()).orElseThrow(SecretRevokedException::new);
        if (entity.status() != SecretStatus.ACTIVE) throw new SecretRevokedException();
        return cipher.decrypt(entity.encrypted());
    }

    @Override
    @Transactional
    public CredentialReference rotate(CredentialReference currentReference, NewSecret replacement,
                                      SecretMetadata metadata) {
        BrokerSecretEntity current = repository
                .findByBrokerAccountIdAndStatus(metadata.brokerAccountId(), SecretStatus.ACTIVE)
                .orElseThrow(SecretRevokedException::new);
        if (!current.id().equals(currentReference.value())) throw new ConcurrentCredentialRotationException();

        long nextVersion = current.secretVersion() + 1;
        UUID nextId = UUID.randomUUID();
        EncryptedSecret encrypted;
        try (PlainSecret plain = new PlainSecret(replacement.copyValue())) {
            encrypted = cipher.encrypt(plain);
        }
        BrokerSecretEntity next = BrokerSecretEntity.pending(nextId, metadata.brokerAccountId(),
                metadata.provider(), encrypted, nextVersion, metadata.apiKeyHint(), clock.instant());
        try {
            repository.saveAndFlush(next);
            current.revoke(nextId, clock.instant());
            repository.saveAndFlush(current);
            next.activate(clock.instant());
            repository.saveAndFlush(next);
            return new CredentialReference(nextId);
        } catch (DataIntegrityViolationException exception) {
            throw new ConcurrentCredentialRotationException();
        }
    }

    @Override
    @Transactional
    public void revoke(CredentialReference reference, String safeReason) {
        BrokerSecretEntity current = repository.findById(reference.value()).orElseThrow(SecretRevokedException::new);
        if (current.status() != SecretStatus.ACTIVE) throw new SecretRevokedException();
        current.revoke(null, clock.instant());
    }

    private CredentialReference createAndActivate(NewSecret secret, SecretMetadata metadata, long version) {
        UUID id = UUID.randomUUID();
        EncryptedSecret encrypted;
        try (PlainSecret plain = new PlainSecret(secret.copyValue())) {
            encrypted = cipher.encrypt(plain);
        }
        BrokerSecretEntity entity = BrokerSecretEntity.pending(id, metadata.brokerAccountId(),
                metadata.provider(), encrypted, version, metadata.apiKeyHint(), clock.instant());
        repository.saveAndFlush(entity);
        entity.activate(clock.instant());
        repository.saveAndFlush(entity);
        return new CredentialReference(id);
    }
}
