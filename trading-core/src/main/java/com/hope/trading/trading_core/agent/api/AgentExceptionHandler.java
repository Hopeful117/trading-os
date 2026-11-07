package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentLifecycleException;
import com.hope.trading.trading_core.agent.application.AgentNotFoundException;
import com.hope.trading.trading_core.agent.application.AgentGrantException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AgentController.class)
public class AgentExceptionHandler {
    @ExceptionHandler(AgentNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(AgentNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Agent was not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(AgentLifecycleException.class)
    ResponseEntity<ProblemDetail> conflict(AgentLifecycleException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(AgentAuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthorized(AgentAuthenticationException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "A human user authentication is required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail);
    }

    @ExceptionHandler(AgentGrantException.class)
    ResponseEntity<ProblemDetail> grantFailure(AgentGrantException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(exception.status()), exception.getMessage());
        detail.setProperty("code", exception.code());
        return ResponseEntity.status(exception.status()).body(detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> grantConstraintFailure(DataIntegrityViolationException exception) {
        if (!isActiveGrantConstraint(exception)) {
            throw exception;
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "An equivalent active grant already exists");
        detail.setProperty("code", "DUPLICATE_ACTIVE_GRANT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    private boolean isActiveGrantConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("uk_agent_grant_active_scope")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
