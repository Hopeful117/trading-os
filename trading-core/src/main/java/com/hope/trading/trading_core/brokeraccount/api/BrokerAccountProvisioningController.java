package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountProvisioningService;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/broker-accounts")
public class BrokerAccountProvisioningController {
    private final BrokerAccountProvisioningService service;

    public BrokerAccountProvisioningController(BrokerAccountProvisioningService service) {
        this.service = service;
    }

    @PostMapping("/{brokerAccountId}/provision-account")
    public ResponseEntity<AccountDto> provision(@PathVariable UUID brokerAccountId,
                                                Authentication authentication) {
        UserDto user = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(service.provision(user.getUserId(), brokerAccountId));
    }
}
