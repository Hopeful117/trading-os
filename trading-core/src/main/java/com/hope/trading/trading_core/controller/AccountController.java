package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.AccountRequest;
import com.hope.trading.trading_core.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    /**
     * Creates a new account based on the provided AccountRequest. The request is validated, and if successful, a new account is created and returned as an AccountDto.
     * @param accountRequest the request containing account details
     * @return the created account as a DTO
     */
    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@RequestBody AccountRequest accountRequest) {
        AccountDto createdAccount = accountService.createAccount(accountRequest);
        return ResponseEntity.ok(createdAccount);
    }

    /**
     * Fetch the account based on the provided account id
     * @param accountId the id of the account
     * @return the account as a DTO
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable UUID accountId) {
        AccountDto accountDto = accountService.getAccountById(accountId);
        return ResponseEntity.ok(accountDto);
    }

    /**
     * Fetch all the accounts
     * @return a list of accounts as a DTO
     */
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        List<AccountDto> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }
}
