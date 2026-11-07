package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentLifecycleException;
import com.hope.trading.trading_core.agent.application.AgentNotFoundException;
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
}
