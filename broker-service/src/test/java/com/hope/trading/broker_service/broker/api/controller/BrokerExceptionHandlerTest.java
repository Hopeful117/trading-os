package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BrokerExceptionHandlerTest {
    @InjectMocks private BrokerExceptionHandler handler;

    @Test
    void authenticationReturnsUnauthorized() {
        ResponseEntity<?> response = handler.authentication(
                new BrokerAuthenticationException("auth failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertProblemBody(response, "BROKER_AUTHENTICATION_FAILED", "Broker authentication failed");
    }

    @Test
    void authorizationReturnsForbidden() {
        ResponseEntity<?> response = handler.authorization(
                new BrokerAuthorizationException("not allowed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertProblemBody(response, "BROKER_AUTHORIZATION_FAILED", "Broker operation is not authorized");
    }

    @Test
    void invalidOrderReturnsUnprocessableEntity() {
        ResponseEntity<?> response = handler.invalidOrder(
                new InvalidOrderException("bad order"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertProblemBody(response, "INVALID_BROKER_ORDER", "Broker rejected the order parameters");
    }

    @Test
    void insufficientFundsReturnsConflict() {
        ResponseEntity<?> response = handler.funds(
                new InsufficientFundsException("no funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertProblemBody(response, "INSUFFICIENT_BROKER_FUNDS", "Broker account has insufficient funds");
    }

    @Test
    void orderNotFoundReturnsNotFound() {
        ResponseEntity<?> response = handler.orderNotFound(
                new BrokerOrderNotFoundException("missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertProblemBody(response, "BROKER_ORDER_NOT_FOUND", "Broker order was not found");
    }

    @Test
    void rateLimitReturnsTooManyRequests() {
        ResponseEntity<?> response = handler.rate(
                new BrokerRateLimitException("slow down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertProblemBody(response, "BROKER_RATE_LIMITED", "Broker rate limit reached");
    }

    @Test
    void unavailableReturnsServiceUnavailable() {
        ResponseEntity<?> response = handler.unavailable(
                new BrokerUnavailableException("down", new RuntimeException("timeout")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertProblemBody(response, "BROKER_UNAVAILABLE", "Broker is temporarily unavailable");
    }

    @Test
    void technicalReturnsBadGateway() {
        ResponseEntity<?> response = handler.technical(
                new BrokerTechnicalException("protocol error"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertProblemBody(response, "BROKER_PROTOCOL_ERROR", "Broker communication failed");
    }

    @SuppressWarnings("unchecked")
    private void assertProblemBody(ResponseEntity<?> response, String expectedCode, String expectedMessage) {
        var problem = (BrokerExceptionHandler.Problem) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.code()).isEqualTo(expectedCode);
        assertThat(problem.message()).isEqualTo(expectedMessage);
        assertThat(problem.timestamp()).isNotNull();
    }
}
