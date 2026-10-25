package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.dashboard.integration.BrokerAccountFact;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.model.PositionIntelligenceView;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.dashboard.service.PositionDataIntegrityException;
import com.hope.trading.trading_core.dashboard.service.PositionDataUnavailableException;
import com.hope.trading.trading_core.dashboard.service.PositionIntelligenceQueryService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
    private final PositionIntelligenceQueryService positionIntelligenceQueryService;
    private final BrokerAccountRepository brokerAccountRepository;

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<OpenPositionDashboardView>> findPositions(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        UserDto user = (UserDto) authentication.getPrincipal();
        Account account = accountService.getAccountById(accountId, user.getUsername());

        BrokerAccount linkedBrokerAccount = account.getBrokerAccountId() == null
                ? null
                : brokerAccountRepository.findById(account.getBrokerAccountId()).orElse(null);
        if (linkedBrokerAccount == null || linkedBrokerAccount.ownerId() == null
                || account.getUser() == null
                || !linkedBrokerAccount.ownerId().equals(account.getUser().getUserId())) {
            throw new PositionDataIntegrityException("Account to BrokerAccount relation is invalid");
        }

        if (linkedBrokerAccount.executionMode() == ExecutionMode.PAPER) {
            return ResponseEntity.ok(positionQueryService.findPaperPositions(account, Instant.now()));
        }
        if (linkedBrokerAccount.executionMode() != ExecutionMode.LIVE) {
            throw new IllegalStateException("Execution mode is unavailable");
        }

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

    @GetMapping("/{accountId}/positions/{positionId}/intelligence")
    public ResponseEntity<PositionIntelligenceView> findPositionIntelligence(
            @PathVariable UUID accountId,
            @PathVariable String positionId,
            Authentication authentication
    ) {
        UserDto user = (UserDto) authentication.getPrincipal();
        Account account = accountService.getAccountById(accountId, user.getUsername());
        BrokerAccount linkedBrokerAccount = requireLinkedBrokerAccount(account);

        List<OpenPositionDashboardView> positions;
        BigDecimal balance;
        BigDecimal equity;
        if (linkedBrokerAccount.executionMode() == ExecutionMode.PAPER) {
            balance = paperBalance(account);
            equity = account.getEquity();
            positions = positionQueryService.findPaperPositions(account, equity, Instant.now());
        } else if (linkedBrokerAccount.executionMode() == ExecutionMode.LIVE) {
            BrokerAccountFact broker;
            try {
                broker = brokerMapper.toFact(brokerApiClient.getAccount());
            } catch (RuntimeException exception) {
                throw new PositionDataUnavailableException("Authoritative position data unavailable", exception);
            }
            balance = broker.balancesAvailable()
                    ? broker.balances().get(account.getBaseCurrency()) : null;
            equity = broker.brokerEquity();
            positions = positionQueryService.findPositions(accountId, broker.positions(), equity, Instant.now(),
                    account.getBaseCurrency());
        } else {
            throw new IllegalStateException("Execution mode is unavailable");
        }

        OpenPositionDashboardView position = positions.stream()
                .filter(candidate -> candidate.positionId().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Open position not found"));
        return ResponseEntity.ok(positionIntelligenceQueryService.query(user.getUserId(), account, position,
                balance, equity));
    }

    private BigDecimal paperBalance(Account account) {
        if (account.getRealizedBalance() != null) return account.getRealizedBalance();
        if (account.getBalances() == null) return null;
        return account.getBalances().stream()
                .filter(value -> value != null && value.getAsset() != null
                        && account.getBaseCurrency().equalsIgnoreCase(value.getAsset()))
                .map(value -> value.getAmount())
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private BrokerAccount requireLinkedBrokerAccount(Account account) {
        BrokerAccount linkedBrokerAccount = account.getBrokerAccountId() == null
                ? null
                : brokerAccountRepository.findById(account.getBrokerAccountId()).orElse(null);
        if (linkedBrokerAccount == null || linkedBrokerAccount.ownerId() == null
                || account.getUser() == null
                || !linkedBrokerAccount.ownerId().equals(account.getUser().getUserId())) {
            throw new PositionDataIntegrityException("Account to BrokerAccount relation is invalid");
        }
        return linkedBrokerAccount;
    }

    @ExceptionHandler(PositionDataIntegrityException.class)
    public ResponseEntity<String> handlePositionDataIntegrity(PositionDataIntegrityException exception) {
        log.error("Position data integrity failure: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Position data integrity failure");
    }

    @ExceptionHandler(PositionDataUnavailableException.class)
    public ResponseEntity<String> handlePositionDataUnavailable(PositionDataUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
    }
}
