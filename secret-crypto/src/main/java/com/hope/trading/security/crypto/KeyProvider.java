package com.hope.trading.security.crypto;

import javax.crypto.SecretKey;

public interface KeyProvider {
    String activeVersion();

    SecretKey activeKey();

    SecretKey key(String version);
}
