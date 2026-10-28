package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dashboard.service.DashboardSnapshotDeferredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {
    @ExceptionHandler(DashboardSnapshotDeferredException.class)
    public ResponseEntity<Map<String, String>> snapshotDeferred(DashboardSnapshotDeferredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "BROKER_SNAPSHOT_DEFERRED", "message", exception.getMessage()));
    }
}
