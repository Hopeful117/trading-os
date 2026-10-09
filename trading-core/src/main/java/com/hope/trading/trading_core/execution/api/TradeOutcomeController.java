package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.api.dto.TradeOutcomeDto;
import com.hope.trading.trading_core.execution.application.service.TradeOutcomeService;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/trade-outcomes")
public class TradeOutcomeController {
    private final TradeOutcomeService service;
    private final AccountRepository accounts;

    public TradeOutcomeController(TradeOutcomeService service, AccountRepository accounts) {
        this.service = service; this.accounts = accounts;
    }

    @GetMapping
    public ResponseEntity<List<TradeOutcomeDto>> list(@PathVariable UUID accountId,
                                                       @RequestParam(required = false) UUID strategyId,
                                                       @RequestParam(required = false) String instrument,
                                                       @RequestParam(required = false) TradeOutcomeStatus status,
                                                       @RequestParam(required = false) Instant from,
                                                       @RequestParam(required = false) Instant to,
                                                       Authentication authentication) {
        requireOwned(accountId, authentication);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        return ResponseEntity.ok(service.findByAccount(accountId, strategyId, instrument, status, from, to).stream()
                .map(TradeOutcomeDto::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradeOutcomeDto> get(@PathVariable UUID accountId, @PathVariable UUID id,
                                                Authentication authentication) {
        requireOwned(accountId, authentication);
        TradeOutcomeDto result;
        try {
            result = TradeOutcomeDto.from(service.find(id));
        } catch (java.util.NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade outcome not found", missing);
        }
        if (!result.accountId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trade outcome does not belong to account");
        }
        return ResponseEntity.ok(result);
    }

    private void requireOwned(UUID accountId, Authentication authentication) {
        UUID userId = ((UserDto) authentication.getPrincipal()).getUserId();
        accounts.findById(accountId)
                .filter(account -> account.getUser() != null && userId.equals(account.getUser().getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Account is not owned by the authenticated user"));
    }
}
