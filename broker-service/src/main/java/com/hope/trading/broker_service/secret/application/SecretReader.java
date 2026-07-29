package com.hope.trading.broker_service.secret.application;

import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;

public interface SecretReader {
    PlainSecret read(CredentialReference reference);
}
