package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.BrokerAccountDto;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.service.AccountService;
import com.hope.trading.trading_core.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AccountController is a REST controller that handles account-related operations in the trading application. It provides endpoints for creating, retrieving, and listing accounts.
 *
 */
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
    public ResponseEntity<AccountDto> getAccount(@PathVariable UUID accountId,@AuthenticationPrincipal UserDetails userDetails) {
        Account account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(accountMapper.toDto(account));
    }

    /**
     * Fetch all the accounts
     * @return a list of accounts as a DTO
     */
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts(@AuthenticationPrincipal UserDetails userDetails) {
        List<AccountDto> accounts = accountService.getAllUserAccounts(userDetails.getUsername());
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/synchronize")
    public ResponseEntity<?> synchronize(Authentication authentication){
        brokerSynchronizationService.synchronize(authentication.getName());
        return ResponseEntity.ok("Accounts synchronized successfully");

    }
}
