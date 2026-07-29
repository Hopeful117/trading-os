package com.hope.trading.broker_service.secret.application;

import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.NewSecret;
import com.hope.trading.broker_service.secret.domain.SecretMetadata;

public interface SecretWriter {
    CredentialReference write(NewSecret secret, SecretMetadata metadata);
}
