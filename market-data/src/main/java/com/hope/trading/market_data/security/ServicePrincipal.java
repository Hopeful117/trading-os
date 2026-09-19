package com.hope.trading.market_data.security;

import java.util.UUID;

public record ServicePrincipal(String serviceName, String audience, UUID actorId) {
}
