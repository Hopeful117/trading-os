package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.dashboard.integration.BrokerAccountFact;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class PositionController {
    private final AccountService accountService;
    private final BrokerApiClient brokerApiClient;
    private final BrokerDashboardMapper brokerMapper;
    private final PositionQueryService positionQueryService;

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<OpenPositionDashboardView>> findPositions(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        UserDto user = (UserDto) authentication.getPrincipal();
        Account account = accountService.getAccountById(accountId, user.getUsername());

        BrokerAccountFact broker;
        try {
            broker = brokerMapper.toFact(brokerApiClient.getAccount());
        } catch (RuntimeException exception) {
            log.warn("Position query broker data unavailable accountId={}", accountId);
            return ResponseEntity.ok(List.of());
        }

        BigDecimal balance = broker.balances()
                .getOrDefault(account.getBaseCurrency(), BigDecimal.ZERO);

        List<OpenPositionDashboardView> positions = positionQueryService.findPositions(
                accountId, broker.positions(), balance, Instant.now()
        );

        return ResponseEntity.ok(positions);
    }
}
