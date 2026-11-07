package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentLifecycleService;
import com.hope.trading.trading_core.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {
    @Mock
    private AgentLifecycleService service;

    @Test
    void rejectsUnauthenticatedPrincipalBeforeOwnerScopedOperation() {
        AgentController controller = new AgentController(service);

        assertThatThrownBy(() -> controller.list(null))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    void rejectsNonHumanPrincipalBeforeOwnerScopedOperation() {
        AgentController controller = new AgentController(service);
        var authentication = new UsernamePasswordAuthenticationToken("service", null);

        assertThatThrownBy(() -> controller.list(authentication))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    void usesTheAuthenticatedHumanUserId() {
        AgentController controller = new AgentController(service);
        UUID ownerId = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(
                UserDto.builder().userId(ownerId).build(), null, java.util.List.of());

        controller.list(authentication);

        verify(service).list(ownerId);
    }
}
