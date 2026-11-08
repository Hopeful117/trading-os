package com.hope.trading.trading_core.agent.operation.api;
import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes = AgentOperationController.class)
public class AgentOperationExceptionHandler {
    @ExceptionHandler(AgentOperationException.class)
    ResponseEntity<ProblemDetail> handle(AgentOperationException e) { var detail = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.status()), e.getMessage()); detail.setProperty("code", e.getMessage()); return ResponseEntity.status(e.status()).body(detail); }
}
