package com.hope.trading.broker_service.security;

import java.util.UUID;

public record ServicePrincipal(String serviceName, String audience, UUID actorId) { }
