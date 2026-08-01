package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import java.time.Instant;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages="com.hope.trading.broker_service.broker.api")
public final class BrokerExceptionHandler {
    @ExceptionHandler(BrokerAuthenticationException.class) ResponseEntity<Problem> authentication(BrokerAuthenticationException e){return response(HttpStatus.UNAUTHORIZED,"BROKER_AUTHENTICATION_FAILED","Broker authentication failed");}
    @ExceptionHandler(BrokerAuthorizationException.class) ResponseEntity<Problem> authorization(BrokerAuthorizationException e){return response(HttpStatus.FORBIDDEN,"BROKER_AUTHORIZATION_FAILED","Broker operation is not authorized");}
    @ExceptionHandler(InvalidOrderException.class) ResponseEntity<Problem> invalidOrder(InvalidOrderException e){return response(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_BROKER_ORDER","Broker rejected the order parameters");}
    @ExceptionHandler(InsufficientFundsException.class) ResponseEntity<Problem> funds(InsufficientFundsException e){return response(HttpStatus.CONFLICT,"INSUFFICIENT_BROKER_FUNDS","Broker account has insufficient funds");}
    @ExceptionHandler(BrokerOrderNotFoundException.class) ResponseEntity<Problem> orderNotFound(BrokerOrderNotFoundException e){return response(HttpStatus.NOT_FOUND,"BROKER_ORDER_NOT_FOUND","Broker order was not found");}
    @ExceptionHandler(BrokerRateLimitException.class) ResponseEntity<Problem> rate(BrokerRateLimitException e){return response(HttpStatus.TOO_MANY_REQUESTS,"BROKER_RATE_LIMITED","Broker rate limit reached");}
    @ExceptionHandler(BrokerUnavailableException.class) ResponseEntity<Problem> unavailable(BrokerUnavailableException e){return response(HttpStatus.SERVICE_UNAVAILABLE,"BROKER_UNAVAILABLE","Broker is temporarily unavailable");}
    @ExceptionHandler(BrokerTechnicalException.class) ResponseEntity<Problem> technical(BrokerTechnicalException e){return response(HttpStatus.BAD_GATEWAY,"BROKER_PROTOCOL_ERROR","Broker communication failed");}
    private ResponseEntity<Problem> response(HttpStatus status,String code,String message){return ResponseEntity.status(status).body(new Problem(code,message,Instant.now()));}
    record Problem(String code,String message,Instant timestamp){}
}
