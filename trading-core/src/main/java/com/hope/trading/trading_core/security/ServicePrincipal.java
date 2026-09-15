package com.hope.trading.trading_core.security;

import java.util.UUID;

public record ServicePrincipal(String serviceName, String audience, UUID actorId) {
}
