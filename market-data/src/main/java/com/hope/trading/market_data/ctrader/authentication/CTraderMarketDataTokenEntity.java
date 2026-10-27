package com.hope.trading.market_data.ctrader.authentication;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "ctrader_market_data_token")
class CTraderMarketDataTokenEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CTraderEnvironment environment;
    @Column(name = "external_account_id", nullable = false)
    private long externalAccountId;
    @Column(name = "access_ciphertext", nullable = false, columnDefinition = "text")
    private String accessCiphertext;
    @Column(name = "access_iv", nullable = false)
    private String accessIv;
    @Column(name = "access_algorithm", nullable = false)
    private String accessAlgorithm;
    @Column(name = "access_key_version", nullable = false)
    private String accessKeyVersion;
    @Column(name = "refresh_ciphertext", nullable = false, columnDefinition = "text")
    private String refreshCiphertext;
    @Column(name = "refresh_iv", nullable = false)
    private String refreshIv;
    @Column(name = "refresh_algorithm", nullable = false)
    private String refreshAlgorithm;
    @Column(name = "refresh_key_version", nullable = false)
    private String refreshKeyVersion;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CTraderTokenStatus status;
    @Column(name = "active_marker", length = 16)
    private String activeMarker;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected CTraderMarketDataTokenEntity() { }

    static CTraderMarketDataTokenEntity active(UUID id, CTraderEnvironment environment, long accountId,
                                                CTraderTokenCryptoAdapter.EncryptedToken access,
                                                CTraderTokenCryptoAdapter.EncryptedToken refresh,
                                                Instant expiresAt, long version, Instant now) {
        CTraderMarketDataTokenEntity entity = new CTraderMarketDataTokenEntity();
        entity.id = id;
        entity.environment = environment;
        entity.externalAccountId = accountId;
        entity.replace(access, refresh, expiresAt, version, now);
        entity.status = CTraderTokenStatus.ACTIVE;
        entity.activeMarker = "ACTIVE";
        return entity;
    }

    void rotate(CTraderTokenCryptoAdapter.EncryptedToken access,
                CTraderTokenCryptoAdapter.EncryptedToken refresh, Instant expiry, Instant now) {
        if (status != CTraderTokenStatus.ACTIVE) throw new IllegalStateException("Token is not active");
        replace(access, refresh, expiry, credentialVersion + 1, now);
    }

    private void replace(CTraderTokenCryptoAdapter.EncryptedToken access,
                         CTraderTokenCryptoAdapter.EncryptedToken refresh, Instant expiry,
                         long version, Instant now) {
        accessCiphertext = access.ciphertext(); accessIv = access.initializationVector();
        accessAlgorithm = access.algorithm(); accessKeyVersion = access.keyVersion();
        refreshCiphertext = refresh.ciphertext(); refreshIv = refresh.initializationVector();
        refreshAlgorithm = refresh.algorithm(); refreshKeyVersion = refresh.keyVersion();
        expiresAt = expiry; credentialVersion = version; updatedAt = now;
    }

    void invalidate(Instant now) { status = CTraderTokenStatus.INVALID; activeMarker = null; updatedAt = now; }
    void revoke(Instant now) { status = CTraderTokenStatus.REVOKED; activeMarker = null; updatedAt = now; }
    UUID id() { return id; }
    CTraderEnvironment environment() { return environment; }
    long externalAccountId() { return externalAccountId; }
    long credentialVersion() { return credentialVersion; }
    Instant expiresAt() { return expiresAt; }
    Instant updatedAt() { return updatedAt; }
    CTraderTokenStatus status() { return status; }
    CTraderTokenCryptoAdapter.EncryptedToken access() {
        return new CTraderTokenCryptoAdapter.EncryptedToken(accessCiphertext, accessIv, accessAlgorithm, accessKeyVersion);
    }
    CTraderTokenCryptoAdapter.EncryptedToken refresh() {
        return new CTraderTokenCryptoAdapter.EncryptedToken(refreshCiphertext, refreshIv, refreshAlgorithm, refreshKeyVersion);
    }
}
