package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.execution.domain.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ExecutionController.class)
public class ExecutionExceptionHandler {
    @ExceptionHandler(ExecutionNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(RuntimeException e){
        return response(HttpStatus.NOT_FOUND,e.getMessage());
    }
    @ExceptionHandler({InvalidExecutionStateException.class,DuplicateExecutionException.class,
            ExecutionExpiredException.class,ExecutionRecoveryException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException e){
        return response(HttpStatus.CONFLICT,e.getMessage());
    }
    @ExceptionHandler(ExecutionValidationException.class)
    ResponseEntity<ProblemDetail> validation(ExecutionValidationException e){
        HttpStatus status = switch (e.status()) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle("Validation Error");
        problem.setProperty("code", e.code());
        return ResponseEntity.status(status).body(problem);
    }
    private ResponseEntity<ProblemDetail> response(HttpStatus status,String message){
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,message));
    }
}
