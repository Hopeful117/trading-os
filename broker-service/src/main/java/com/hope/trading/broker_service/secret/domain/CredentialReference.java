package com.hope.trading.broker_service.secret.domain;

import java.util.Objects;
import java.util.UUID;

public record CredentialReference(UUID value) {
    public CredentialReference {
        Objects.requireNonNull(value, "value is required");
    }

    @Override
    public String toString() {
        return "CredentialReference[opaque]";
    }
}
