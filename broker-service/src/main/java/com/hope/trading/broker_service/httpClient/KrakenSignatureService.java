package com.hope.trading.broker_service.httpClient;

import java.util.Map;

public interface KrakenSignatureService {
    String generateSignature(String path, Map<String,String> body);
}
