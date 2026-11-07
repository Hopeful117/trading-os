package com.hope.trading.trading_core.agent.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCredentialGeneratorTest {
    private final AgentCredentialGenerator generator = new AgentCredentialGenerator();

    @Test
    void generatesDistinctHighEntropyMaterialAndDigest() {
        AgentCredentialGenerator.Material first = generator.generate();
        AgentCredentialGenerator.Material second = generator.generate();

        assertThat(first.raw()).isNotBlank().hasSizeGreaterThan(40);
        assertThat(first.digest()).hasSize(64).isNotEqualTo(first.raw());
        assertThat(first.fingerprint()).hasSize(16);
        assertThat(first.raw()).isNotEqualTo(second.raw());
        assertThat(first.digest()).isNotEqualTo(second.digest());
    }
}
