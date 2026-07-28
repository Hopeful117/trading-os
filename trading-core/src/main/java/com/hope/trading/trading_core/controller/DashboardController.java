package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dashboard.model.DashboardSummary;
import com.hope.trading.trading_core.dashboard.service.DashboardQueryService;
import com.hope.trading.trading_core.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardQueryService dashboardQueryService;

    @GetMapping("/{accountId}/dashboard")
    public ResponseEntity<DashboardSummary> findDashboard(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        UserDto user = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(
                dashboardQueryService.findDashboard(accountId, user.getUsername())
        );
    }
}
