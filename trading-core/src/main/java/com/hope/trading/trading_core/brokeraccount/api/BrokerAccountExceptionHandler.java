package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountNotFoundException;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountOwnershipException;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountSnapshotIncompleteException;
import com.hope.trading.trading_core.controller.AccountSynchronizationV2Controller;
import com.hope.trading.trading_core.brokeraccount.domain.InvalidBrokerConnectionTransitionException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        BrokerAccountController.class,
        BrokerAccountProvisioningController.class,
        InternalBrokerAccountController.class,
        AccountSynchronizationV2Controller.class
})
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

    @ExceptionHandler(BrokerAccountSnapshotIncompleteException.class)
    ResponseEntity<ProblemDetail> snapshotIncomplete(BrokerAccountSnapshotIncompleteException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setProperty("code", "BROKER_ACCOUNT_SNAPSHOT_INCOMPLETE");
        detail.setProperty("completeness", exception.completeness());
        detail.setProperty("freshness", exception.freshness());
        detail.setProperty("reasons", exception.reasons());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(FeignException.Conflict.class)
    ResponseEntity<ProblemDetail> snapshotDeferred(FeignException.Conflict exception) {
        String content = exception.contentUTF8();
        boolean deferred = content != null && content.contains("BROKER_SNAPSHOT_DEFERRED");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                deferred ? "Broker account snapshot is temporarily unavailable"
                        : "Broker provider rejected the request");
        detail.setProperty("code", deferred ? "BROKER_SNAPSHOT_DEFERRED" : "BROKER_PROVIDER_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }
}
