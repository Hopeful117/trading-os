package com.hope.trading.market_data.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarketDataExceptionHandler {
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Void> handleUnsupportedOperation() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
