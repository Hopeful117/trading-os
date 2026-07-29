package com.hope.trading.broker_service.connection.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SubmitBrokerCredentialsRequest {
    @NotBlank
    @Size(min = 8, max = 256)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;
    @NotBlank
    @Size(min = 16, max = 512)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiSecret;
    @Size(max = 256)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passphrase;

    public String apiKey() { return apiKey; }
    public String apiSecret() { return apiSecret; }
    public String passphrase() { return passphrase; }

    @Override
    public String toString() {
        return "SubmitBrokerCredentialsRequest[REDACTED]";
    }
}
