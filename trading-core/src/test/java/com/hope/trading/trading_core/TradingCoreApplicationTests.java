package com.hope.trading.trading_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.execution.application.service.ValidateAndCreateService;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TradingCoreApplicationTests {

	@Autowired
	private ValidateAndCreateService validation;

	@Test
	void contextLoads() {
	}

	@Test
	void validationServiceIsTransactionallyProxied() {
		assertThat(AopUtils.isAopProxy(validation)).isTrue();
	}

}
