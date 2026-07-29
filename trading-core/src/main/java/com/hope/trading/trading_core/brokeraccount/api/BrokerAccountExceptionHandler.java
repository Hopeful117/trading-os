package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountNotFoundException;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountOwnershipException;
import com.hope.trading.trading_core.brokeraccount.domain.InvalidBrokerConnectionTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {BrokerAccountController.class, InternalBrokerAccountController.class})
public class BrokerAccountExceptionHandler {
    @ExceptionHandler({BrokerAccountNotFoundException.class, BrokerAccountOwnershipException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Broker account was not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(InvalidBrokerConnectionTransitionException.class)
    ResponseEntity<ProblemDetail> conflict(InvalidBrokerConnectionTransitionException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }
}
