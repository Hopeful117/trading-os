package com.hope.trading.trading_core.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Prevents unhandled persistence conflicts from surfacing as misleading
 * HTTP 403 responses. A unique-constraint violation is a conflict, not
 * a security decision: it must be reported as 409 Conflict.
 */
@Slf4j
@RestControllerAdvice
public class DataIntegrityExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        log.warn("Data integrity violation rejected: {}",
                exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(409)
                .body("Operation conflicts with existing data");
    }
}
