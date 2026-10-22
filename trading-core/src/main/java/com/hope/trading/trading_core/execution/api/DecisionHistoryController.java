package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.api.dto.DecisionHistoryItemDto;
import com.hope.trading.trading_core.execution.application.service.DecisionHistoryService;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/decision-history")
public class DecisionHistoryController {
    private final DecisionHistoryService history;
    private final AccountRepository accounts;

    public DecisionHistoryController(DecisionHistoryService history, AccountRepository accounts) {
        this.history = history;
        this.accounts = accounts;
    }

    @GetMapping
    public ResponseEntity<List<DecisionHistoryItemDto>> list(@PathVariable UUID accountId,
                                                              Authentication authentication) {
        UUID userId = ((UserDto) authentication.getPrincipal()).getUserId();
        accounts.findById(accountId)
                .filter(account -> account.getUser() != null && userId.equals(account.getUser().getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Account is not owned by the authenticated user"));
        return ResponseEntity.ok(history.findByAccount(accountId));
    }
}
