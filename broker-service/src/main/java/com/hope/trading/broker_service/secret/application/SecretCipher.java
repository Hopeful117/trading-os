package com.hope.trading.broker_service.secret.application;

import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;

public interface SecretCipher {
    EncryptedSecret encrypt(PlainSecret secret);
    PlainSecret decrypt(EncryptedSecret secret);
}
