package com.hope.trading.news.security;

import java.util.UUID;

public record NewsUserPrincipal(UUID userId, String username, String email) {
}
