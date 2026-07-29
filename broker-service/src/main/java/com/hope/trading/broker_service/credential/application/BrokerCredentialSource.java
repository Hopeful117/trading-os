package com.hope.trading.broker_service.credential.application;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.domain.CredentialReference;

public interface BrokerCredentialSource {
    CredentialMaterial resolve(CredentialReference reference);
}
