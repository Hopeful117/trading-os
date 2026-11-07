package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentLifecycleService;
import com.hope.trading.trading_core.agent.application.AgentGrantService;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.agent.model.AgentGrantStatus;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {
    @Mock
    private AgentLifecycleService service;

    @Mock
    private AgentGrantService grants;

    @Test
    void rejectsUnauthenticatedPrincipalBeforeOwnerScopedOperation() {
        AgentController controller = new AgentController(service, grants);

        assertThatThrownBy(() -> controller.list(null))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    void rejectsNonHumanPrincipalBeforeOwnerScopedOperation() {
        AgentController controller = new AgentController(service, grants);
        var authentication = new UsernamePasswordAuthenticationToken("service", null);

        assertThatThrownBy(() -> controller.list(authentication))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    void rejectsUnauthenticatedGrantManagementBeforeDelegation() {
        AgentController controller = new AgentController(service, grants);

        assertThatThrownBy(() -> controller.createGrant(UUID.randomUUID(), null, null, null))
                .isInstanceOf(AgentAuthenticationException.class);
        assertThatThrownBy(() -> controller.listGrants(UUID.randomUUID(), null))
                .isInstanceOf(AgentAuthenticationException.class);
        verifyNoInteractions(grants);
    }

    @Test
    void usesTheAuthenticatedHumanUserId() {
        AgentController controller = new AgentController(service, grants);
        UUID ownerId = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(
                UserDto.builder().userId(ownerId).build(), null, java.util.List.of());

        controller.list(authentication);

        verify(service).list(ownerId);
    }

    @Test
    void createsGrantUsingTheAuthenticatedHumanUserId() {
        AgentController controller = new AgentController(service, grants);
        UUID ownerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        var authentication = human(ownerId);
        var request = new AgentGrantRequest(AgentPermission.MARKETS_READ, AgentResourceType.MARKET, resourceId, null);
        var response = new AgentGrantResponse(UUID.randomUUID(), agentId, AgentPermission.MARKETS_READ,
                AgentResourceType.MARKET, resourceId, AgentGrantStatus.ACTIVE, Instant.now(), null, null, null, 0);
        when(grants.create(ownerId, agentId, request, "correlation")).thenReturn(response);

        assertThat(controller.createGrant(agentId, request, authentication, "correlation").getBody())
                .isEqualTo(response);
        verify(grants).create(ownerId, agentId, request, "correlation");
    }

    @Test
    void listsGrantsForTheAuthenticatedHumanUserId() {
        AgentController controller = new AgentController(service, grants);
        UUID ownerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        controller.listGrants(agentId, human(ownerId));

        verify(grants).list(ownerId, agentId);
    }

    @Test
    void getsAndRevokesOnlyTheRequestedOwnedGrant() {
        AgentController controller = new AgentController(service, grants);
        UUID ownerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        controller.getGrant(agentId, grantId, human(ownerId));
        controller.revokeGrant(agentId, grantId, human(ownerId), "correlation");

        verify(grants).get(ownerId, agentId, grantId);
        verify(grants).revoke(ownerId, agentId, grantId, "correlation");
    }

    private UsernamePasswordAuthenticationToken human(UUID ownerId) {
        return new UsernamePasswordAuthenticationToken(
                UserDto.builder().userId(ownerId).build(), null, java.util.List.of());
    }
}
