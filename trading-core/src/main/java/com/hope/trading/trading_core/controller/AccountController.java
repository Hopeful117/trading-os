package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.service.AccountService;
import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AccountController is a REST controller that handles account-related operations in the trading application. It provides endpoints for creating, retrieving, and listing accounts.
 *
 */
@Slf4j
@Controller
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    private final BrokerSynchronizationService brokerSynchronizationService;
    private final AccountMapper accountMapper;


    /**
     * Fetch the account based on the provided account id
     * @param accountId the id of the account
     * @return the account as a DTO
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable UUID accountId,Authentication authentication) {
        log.debug("Account requested accountId={}", accountId);
        UserDto user =
                (UserDto) authentication.getPrincipal();
        assert user != null;
        Account account = accountService.getAccountById(accountId, user.getUsername());
        return ResponseEntity.ok(accountMapper.toDto(account));
    }

    /**
     * Fetch all the accounts
     * @return a list of accounts as a DTO
     */
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts(Authentication authentication) {
        log.debug("Account list requested");
        UserDto user =
                (UserDto) authentication.getPrincipal();

        assert user != null;
        List<AccountDto> accounts = accountService.getAllUserAccounts(user.getUsername());
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/synchronize")
    public ResponseEntity<?> synchronize(Authentication authentication){
        log.info("synchronizing account for user {}",authentication.getPrincipal());
        UserDto user =
                (UserDto) authentication.getPrincipal();

        assert user != null;
        brokerSynchronizationService.synchronize(user.getUsername());
        return ResponseEntity.ok("Accounts synchronized successfully");

    }
}
