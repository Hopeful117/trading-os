package com.hope.trading.news.security;

import java.util.UUID;

public record NewsServicePrincipal(String serviceName, String audience, UUID actorId) {
}
