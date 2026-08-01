package com.hope.trading.broker_service.broker.domain.exception;

public final class BrokerExceptions {
    private BrokerExceptions() {}
    public static class BrokerTechnicalException extends RuntimeException { public BrokerTechnicalException(String message){super(message);} public BrokerTechnicalException(String message,Throwable cause){super(message,cause);} }
    public static final class BrokerAuthenticationException extends BrokerTechnicalException { public BrokerAuthenticationException(String message){super(message);} }
    public static final class BrokerAuthorizationException extends BrokerTechnicalException { public BrokerAuthorizationException(String message){super(message);} }
    public static final class InvalidOrderException extends BrokerTechnicalException { public InvalidOrderException(String message){super(message);} }
    public static final class InsufficientFundsException extends BrokerTechnicalException { public InsufficientFundsException(String message){super(message);} }
    public static final class BrokerOrderNotFoundException extends BrokerTechnicalException { public BrokerOrderNotFoundException(String message){super(message);} }
    public static final class BrokerRateLimitException extends BrokerTechnicalException { public BrokerRateLimitException(String message){super(message);} }
    public static final class BrokerUnavailableException extends BrokerTechnicalException { public BrokerUnavailableException(String message,Throwable cause){super(message,cause);} }
    public static final class BrokerProtocolException extends BrokerTechnicalException { public BrokerProtocolException(String message){super(message);} }
    public static final class UnknownBrokerException extends BrokerTechnicalException { public UnknownBrokerException(String message,Throwable cause){super(message,cause);} }
    public static final class UnsupportedBrokerProviderException extends BrokerTechnicalException { public UnsupportedBrokerProviderException(String message){super(message);} }
}
