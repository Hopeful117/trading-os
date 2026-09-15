package com.hope.trading.market_intelligence.security;

import java.util.UUID;

public record MiUserPrincipal(UUID userId, String username, String email) {
}
