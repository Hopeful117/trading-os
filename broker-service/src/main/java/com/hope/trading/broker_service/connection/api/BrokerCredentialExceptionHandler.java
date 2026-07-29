package com.hope.trading.broker_service.connection.api;

import com.hope.trading.broker_service.connection.application.BrokerAccountOwnershipException;
import com.hope.trading.broker_service.connection.application.BrokerConnectionNotFoundException;
import com.hope.trading.broker_service.connection.application.CredentialRateLimitExceededException;
import com.hope.trading.broker_service.secret.application.ConcurrentCredentialRotationException;
import com.hope.trading.broker_service.secret.application.SecretDecryptionException;
import com.hope.trading.broker_service.secret.application.SecretEncryptionException;
import com.hope.trading.broker_service.secret.application.SecretRevokedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BrokerCredentialController.class)
public class BrokerCredentialExceptionHandler {
    @ExceptionHandler({BrokerConnectionNotFoundException.class, BrokerAccountOwnershipException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "Broker account was not found");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidPayload(MethodArgumentNotValidException exception) {
        return response(HttpStatus.BAD_REQUEST, "Credential payload has an invalid format");
    }

    @ExceptionHandler({ConcurrentCredentialRotationException.class, SecretRevokedException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({SecretEncryptionException.class, SecretDecryptionException.class})
    ResponseEntity<ProblemDetail> secretUnavailable(RuntimeException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Protected credential storage is unavailable");
    }

    @ExceptionHandler(CredentialRateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(CredentialRateLimitExceededException exception) {
        return response(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }
}
