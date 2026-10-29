package com.hope.trading.market_data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ctrader.market-data.enabled=true",
        "ctrader.market-data.bootstrap-enabled=false",
        "ctrader.market-data.environment=DEMO",
        "ctrader.market-data.client-id=test-client",
        "ctrader.market-data.client-secret=test-secret",
        "ctrader.market-data.websocket-endpoint=wss://demo.ctraderapi.com:5036",
        "ctrader.market-data.token-encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "ctrader.market-data.token-encryption.key-version=v1"
})
class MarketDataApplicationTests {

	@Test
	void contextLoads() {
	}

}
