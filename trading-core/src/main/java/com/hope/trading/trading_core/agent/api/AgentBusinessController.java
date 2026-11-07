package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountReadOnlyStatusResponse;
import com.hope.trading.trading_core.brokeraccount.api.BrokerAccountResponse;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountReadOnlyStatusService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dashboard.integration.BrokerAccountFact;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/broker-accounts")
@RequiredArgsConstructor
public class AgentBusinessController {
    private final BrokerAccountRepository brokerAccounts;
    private final AccountRepository accounts;
    private final AgentAuthorizationService authorization;
    private final BrokerAccountReadOnlyStatusService readOnlyStatus;
    private final PositionQueryService positions;
    private final BrokerAccountSnapshotV2Client broker;
    private final BrokerDashboardMapper brokerMapper;

    @GetMapping
    public ResponseEntity<List<BrokerAccountResponse>> list(
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        AgentPrincipal agent = requireAgent(authentication);
        List<BrokerAccountResponse> result = brokerAccounts.findAllByOwnerIdOrderByCreatedAtDesc(agent.ownerUserId())
                .stream()
                .filter(account -> allowed(agent, AgentPermission.ACCOUNTS_READ, account.id(), correlationId))
                .map(this::map)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{brokerAccountId}")
    public ResponseEntity<BrokerAccountResponse> get(
            @PathVariable UUID brokerAccountId,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        AgentPrincipal agent = requireAgent(authentication);
        BrokerAccount account = ownedAccount(agent.ownerUserId(), brokerAccountId);
        requireGrant(agent, AgentPermission.ACCOUNTS_READ, brokerAccountId, correlationId);
        return ResponseEntity.ok(map(account));
    }

    @GetMapping("/{brokerAccountId}/read-only-status")
    public ResponseEntity<BrokerAccountReadOnlyStatusResponse> readOnlyStatus(
            @PathVariable UUID brokerAccountId,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        AgentPrincipal agent = requireAgent(authentication);
        ownedAccount(agent.ownerUserId(), brokerAccountId);
        requireGrant(agent, AgentPermission.ACCOUNTS_READ, brokerAccountId, correlationId);
        return ResponseEntity.ok(readOnlyStatus.status(agent.ownerUserId(), brokerAccountId));
    }

    @GetMapping("/{brokerAccountId}/positions")
    public ResponseEntity<List<OpenPositionDashboardView>> positions(
            @PathVariable UUID brokerAccountId,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        AgentPrincipal agent = requireAgent(authentication);
        requireGrant(agent, AgentPermission.POSITIONS_READ, brokerAccountId, correlationId);
        BrokerAccount brokerAccount = ownedAccount(agent.ownerUserId(), brokerAccountId);
        Account account = accounts.findByBrokerAccountId(brokerAccountId)
                .filter(value -> value.getUser() != null && agent.ownerUserId().equals(value.getUser().getUserId()))
                .orElseThrow(AgentBusinessAuthorizationException::new);

        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            return ResponseEntity.ok(positions.findPaperPositions(account, Instant.now()));
        }
        if (brokerAccount.executionMode() != ExecutionMode.LIVE) {
            return ResponseEntity.ok(List.of());
        }

        try {
            BrokerAccountSnapshotV2Dto snapshot = broker.getAccountSnapshot(brokerAccountId);
            if (snapshot == null) {
                throw new AgentBusinessDataUnavailableException();
            }
            BrokerAccountFact facts = brokerMapper.toFact(snapshot,
                    brokerAccount.providerRegistrationReference().toString(), account.getBaseCurrency());
            BigDecimal balance = facts.balances().getOrDefault(account.getBaseCurrency(), BigDecimal.ZERO);
            return ResponseEntity.ok(positions.findPositions(account.getAccountId(), facts.positions(), balance, Instant.now()));
        } catch (RuntimeException unavailable) {
            if (unavailable instanceof AgentBusinessDataUnavailableException) {
                throw unavailable;
            }
            throw new AgentBusinessDataUnavailableException();
        }
    }

    private AgentPrincipal requireAgent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal agent)) {
            throw new AgentBusinessAuthorizationException();
        }
        return agent;
    }

    private void requireGrant(AgentPrincipal agent, AgentPermission permission, UUID resourceId,
                              String correlationId) {
        if (!allowed(agent, permission, resourceId, correlationId)) {
            throw new AgentBusinessAuthorizationException();
        }
    }

    private boolean allowed(AgentPrincipal agent, AgentPermission permission, UUID resourceId,
                            String correlationId) {
        return authorization.authorize(agent.agentId(), permission, AgentResourceType.BROKER_ACCOUNT,
                resourceId, correlationId).allowed();
    }

    private BrokerAccount ownedAccount(UUID ownerId, UUID brokerAccountId) {
        return brokerAccounts.findByIdAndOwnerId(brokerAccountId, ownerId)
                .orElseThrow(AgentBusinessAuthorizationException::new);
    }

    private BrokerAccountResponse map(BrokerAccount account) {
        return new BrokerAccountResponse(account.id(), account.providerRegistrationReference(),
                account.executionMode(), account.displayName(), account.externalAccountId(),
                account.connectionStatus(), account.lastValidatedAt(), account.lastSynchronizedAt(),
                account.createdAt(), account.updatedAt());
    }
}
