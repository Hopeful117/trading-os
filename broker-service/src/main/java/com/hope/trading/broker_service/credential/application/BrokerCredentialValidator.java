package com.hope.trading.broker_service.credential.application;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.credential.domain.CredentialValidationResult;

public interface BrokerCredentialValidator {
    BrokerProviderId provider();
    CredentialValidationResult validate(CredentialMaterial credentials);
}
