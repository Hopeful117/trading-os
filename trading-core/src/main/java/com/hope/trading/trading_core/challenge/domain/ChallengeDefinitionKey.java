package com.hope.trading.trading_core.challenge.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ChallengeDefinitionKey implements Serializable {
    private UUID id;
    private String definitionVersion;

    protected ChallengeDefinitionKey() {
    }

    public ChallengeDefinitionKey(UUID id, String definitionVersion) {
        this.id = id;
        this.definitionVersion = definitionVersion;
    }

    public UUID id() {
        return id;
    }

    public String definitionVersion() {
        return definitionVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChallengeDefinitionKey that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(definitionVersion, that.definitionVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, definitionVersion);
    }
}
