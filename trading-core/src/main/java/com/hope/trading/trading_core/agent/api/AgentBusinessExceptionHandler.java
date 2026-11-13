package com.hope.trading.trading_core.agent.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AgentBusinessController.class)
public class AgentBusinessExceptionHandler {
    @ExceptionHandler(AgentBusinessAuthorizationException.class)
    ResponseEntity<ProblemDetail> authorizationDenied(AgentBusinessAuthorizationException exception,
                                                       HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Agent account access was denied", "ACCOUNT_ACCESS_DENIED", request);
    }

    @ExceptionHandler(AgentBusinessDataUnavailableException.class)
    ResponseEntity<ProblemDetail> dataUnavailable(AgentBusinessDataUnavailableException exception,
                                                  HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Authorized broker data is temporarily unavailable",
                "UPSTREAM_UNAVAILABLE", request);
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code,
                                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId.strip());
        }
        return ResponseEntity.status(status).body(problem);
    }
}
