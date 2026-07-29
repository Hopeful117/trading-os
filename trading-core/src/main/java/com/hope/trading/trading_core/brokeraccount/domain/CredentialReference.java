package com.hope.trading.trading_core.brokeraccount.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.UUID;

@Embeddable
public record CredentialReference(UUID value) {
    public CredentialReference {
        Objects.requireNonNull(value, "value is required");
    }

    @Override
    public String toString() {
        return "CredentialReference[opaque]";
    }
}
