package com.hope.trading.broker_service.security;

import java.util.UUID;

public record BrokerPrincipal(UUID userId, String username, String role) {
}
