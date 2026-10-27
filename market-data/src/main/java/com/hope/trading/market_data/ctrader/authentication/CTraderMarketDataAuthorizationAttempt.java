package com.hope.trading.market_data.ctrader.authentication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ctrader_market_data_authorization_attempt")
public class CTraderMarketDataAuthorizationAttempt {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false, length = 128)
    private String stateHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false, length = 16)
    private String status = "INITIATED";

    private String environment;
    private Long externalAccountId;
    private String failureCode;

    @Version
    private long rowVersion;

    protected CTraderMarketDataAuthorizationAttempt() {
    }

    public static CTraderMarketDataAuthorizationAttempt create(String stateHash, Instant expiresAt) {
        CTraderMarketDataAuthorizationAttempt attempt = new CTraderMarketDataAuthorizationAttempt();
        attempt.id = UUID.randomUUID();
        attempt.stateHash = stateHash;
        attempt.expiresAt = expiresAt;
        return attempt;
    }

    public void consume(Instant now) {
        if (consumed || !now.isBefore(expiresAt)) {
            throw new CTraderAuthorizationException("cTrader authorization state is invalid or expired");
        }
        consumed = true;
    }

    public void complete(String environment, long externalAccountId) {
        status = "COMPLETED";
        this.environment = environment;
        this.externalAccountId = externalAccountId;
    }

    public void fail(String code) {
        status = "FAILED";
        failureCode = code;
    }

    public UUID id() { return id; }
    public String stateHash() { return stateHash; }
    public Instant expiresAt() { return expiresAt; }
    public String status() { return status; }
    public String environment() { return environment; }
    public Long externalAccountId() { return externalAccountId; }
    public String failureCode() { return failureCode; }
    public boolean consumed() { return consumed; }
}
