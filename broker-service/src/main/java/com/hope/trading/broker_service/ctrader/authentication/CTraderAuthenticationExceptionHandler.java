package com.hope.trading.broker_service.ctrader.authentication;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CTraderAuthenticationExceptionHandler {
    @ExceptionHandler(CTraderAuthenticationException.class)
    public ResponseEntity<Map<String, String>> handle(CTraderAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(CTraderSnapshotDeferredException.class)
    public ResponseEntity<Map<String, String>> handleSnapshotDeferred(
            CTraderSnapshotDeferredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "BROKER_SNAPSHOT_DEFERRED", "message", exception.getMessage()));
    }
}
