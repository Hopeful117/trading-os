package com.hope.trading.trading_core.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExceptionHandlerTest {
    private final AgentExceptionHandler handler = new AgentExceptionHandler();

    @Test
    void mapsOnlyTheActiveGrantConstraintToTheGrantConflict() {
        var response = handler.grantConstraintFailure(new DataIntegrityViolationException(
                "duplicate key uk_agent_grant_active_scope"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getProperties()).containsEntry("code", "DUPLICATE_ACTIVE_GRANT");
    }

    @Test
    void doesNotTranslateUnrelatedAgentIntegrityFailuresAsGrantConflicts() {
        var exception = new DataIntegrityViolationException("agent credential constraint");

        assertThatThrownBy(() -> handler.grantConstraintFailure(exception))
                .isSameAs(exception);
    }
}
