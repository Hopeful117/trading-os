package com.hope.trading.broker_service.secret.application;

import javax.crypto.SecretKey;

public interface KeyProvider {
    String activeVersion();
    SecretKey activeKey();
    SecretKey key(String version);
}
