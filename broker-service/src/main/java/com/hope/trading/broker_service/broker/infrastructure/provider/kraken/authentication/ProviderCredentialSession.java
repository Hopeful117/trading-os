package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.UUID;
import java.util.function.Function;

public interface ProviderCredentialSession { <T>T withCredentials(UUID accountId, Function<CredentialMaterial,T> operation); }
