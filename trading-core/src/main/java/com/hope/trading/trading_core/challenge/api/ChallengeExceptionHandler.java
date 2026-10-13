package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.application.ChallengeConfigurationException;
import com.hope.trading.trading_core.challenge.application.ChallengeConflictException;
import com.hope.trading.trading_core.challenge.application.ChallengeNotFoundException;
import com.hope.trading.trading_core.challenge.application.ChallengeNotStartedException;
import com.hope.trading.trading_core.challenge.application.ChallengeOwnershipException;
import com.hope.trading.trading_core.challenge.application.ChallengeValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ChallengeLifecycleController.class, ChallengeProgressController.class})
public class ChallengeExceptionHandler {
    @ExceptionHandler({ChallengeNotFoundException.class, ChallengeOwnershipException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "Challenge resource was not found");
    }

    @ExceptionHandler(ChallengeNotStartedException.class)
    ResponseEntity<ProblemDetail> notStarted(ChallengeNotStartedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No Challenge is active for this account");
        problem.setProperty("code", "CHALLENGE_NOT_STARTED");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ChallengeConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ChallengeConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ChallengeValidationException.class)
    ResponseEntity<ProblemDetail> validation(ChallengeValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequest(MethodArgumentNotValidException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> unreadableRequest(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request could not be read");
    }

    @ExceptionHandler(ChallengeConfigurationException.class)
    ResponseEntity<ProblemDetail> configuration(ChallengeConfigurationException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Challenge configuration is unavailable");
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(problem);
    }
}
