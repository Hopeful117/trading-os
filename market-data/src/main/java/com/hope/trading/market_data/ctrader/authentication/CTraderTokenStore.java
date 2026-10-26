package com.hope.trading.market_data.ctrader.authentication;

import java.util.Optional;
import java.util.UUID;

public interface CTraderTokenStore {
    Optional<StoredCTraderToken> findActive();

    StoredCTraderToken create(CTraderEnvironment environment, long externalAccountId,
                              CTraderTokenMaterial material, long credentialVersion);

    StoredCTraderToken rotate(UUID id, long expectedCredentialVersion,
                              CTraderTokenMaterial replacement);

    void markInvalid(UUID id);

    void revoke(UUID id);

    record StoredCTraderToken(CTraderTokenRecord record, CTraderTokenMaterial material) implements AutoCloseable {
        @Override
        public void close() {
            material.close();
        }
    }
}
