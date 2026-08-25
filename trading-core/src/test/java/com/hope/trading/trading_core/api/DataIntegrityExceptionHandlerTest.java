package com.hope.trading.trading_core.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class DataIntegrityExceptionHandlerTest {

    private final DataIntegrityExceptionHandler handler = new DataIntegrityExceptionHandler();

    @Test
    void uniqueConstraintViolationBecomes409Not403() {
        var response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value "
                        + "violates unique constraint \"accounts_user_broker_key\""));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo("Operation conflicts with existing data");
    }

    @Test
    void mostSpecificCauseIsUsedForLoggingWithoutLeakingSecrets() {
        var rootCause = new RuntimeException("duplicate key on accounts");
        var exception = new DataIntegrityViolationException("could not execute", rootCause);

        assertThat(handler.handleDataIntegrityViolation(exception)
                .getStatusCode().value()).isEqualTo(409);
    }
}
