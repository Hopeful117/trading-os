package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.broker.service.LiveCfdReconciliationService;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSynchronizationResult;
import com.hope.trading.trading_core.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/accounts")
public class AccountSynchronizationV2Controller {
    private final BrokerSynchronizationService synchronization;

    public AccountSynchronizationV2Controller(BrokerSynchronizationService synchronization) {
        this.synchronization = synchronization;
    }

    @PostMapping("/{brokerAccountId}/synchronize")
    public ResponseEntity<BrokerAccountSynchronizationResult> synchronize(
            @PathVariable UUID brokerAccountId, Authentication authentication) {
        UserDto user = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(synchronization.synchronize(user.getUserId(), brokerAccountId));
    }

    @GetMapping("/{brokerAccountId}/live-cfd-reconciliation")
    public ResponseEntity<LiveCfdReconciliationService.Result> reconcileLiveCfd(
            @PathVariable UUID brokerAccountId, Authentication authentication) {
        UserDto user = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(synchronization.reconcileLiveCfd(user.getUserId(), brokerAccountId));
    }
}
