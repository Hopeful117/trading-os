package com.hope.trading.trading_core.execution.api;

import com.hope.trading.trading_core.execution.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionExceptionHandlerTest {

    private final ExecutionExceptionHandler handler = new ExecutionExceptionHandler();

    @Test
    void executionNotFoundExceptionReturns404() {
        ExecutionNotFoundException ex = new ExecutionNotFoundException();

        ResponseEntity<ProblemDetail> response = handler.notFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Execution not found");
    }

    @Test
    void invalidExecutionStateExceptionReturns409() {
        InvalidExecutionStateException ex = new InvalidExecutionStateException("bad transition");

        ResponseEntity<ProblemDetail> response = handler.conflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("bad transition");
    }

    @Test
    void duplicateExecutionExceptionReturns409() {
        DuplicateExecutionException ex = new DuplicateExecutionException();

        ResponseEntity<ProblemDetail> response = handler.conflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("already exists");
    }

    @Test
    void executionExpiredExceptionReturns409() {
        ExecutionExpiredException ex = new ExecutionExpiredException();

        ResponseEntity<ProblemDetail> response = handler.conflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("expired");
    }

    @Test
    void executionRecoveryExceptionReturns409() {
        ExecutionRecoveryException ex = new ExecutionRecoveryException("recovery failed");

        ResponseEntity<ProblemDetail> response = handler.conflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("recovery failed");
    }

    @Test
    void validationExceptionWith403ReturnsForbidden() {
        ExecutionValidationException ex = new ExecutionValidationException("FORBIDDEN", "not allowed", 403);

        ResponseEntity<ProblemDetail> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("not allowed");
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Error");
        assertThat(response.getBody().getProperties()).containsEntry("code", "FORBIDDEN");
    }

    @Test
    void validationExceptionWith404ReturnsNotFound() {
        ExecutionValidationException ex = new ExecutionValidationException("NOT_FOUND", "gone", 404);

        ResponseEntity<ProblemDetail> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("gone");
    }

    @Test
    void validationExceptionWith409ReturnsConflict() {
        ExecutionValidationException ex = new ExecutionValidationException("STALE", "conflict", 409);

        ResponseEntity<ProblemDetail> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("conflict");
    }

    @Test
    void validationExceptionWith422ReturnsUnprocessableEntity() {
        ExecutionValidationException ex = new ExecutionValidationException("INVALID", "bad data", 422);

        ResponseEntity<ProblemDetail> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("bad data");
    }

    @Test
    void validationExceptionWithUnknownStatusReturnsBadRequest() {
        ExecutionValidationException ex = new ExecutionValidationException("UNKNOWN", "something", 500);

        ResponseEntity<ProblemDetail> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("something");
    }

    @Test
    void problemDetailHasCorrectStructure() {
        ExecutionNotFoundException ex = new ExecutionNotFoundException();

        ResponseEntity<ProblemDetail> response = handler.notFound(ex);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getDetail()).isEqualTo("Execution not found");
    }
}
