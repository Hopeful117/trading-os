package com.hope.trading.broker_service.secret.application;

import com.hope.trading.broker_service.secret.domain.CredentialReference;

public interface SecretRevoker {
    void revoke(CredentialReference reference, String safeReason);
}
