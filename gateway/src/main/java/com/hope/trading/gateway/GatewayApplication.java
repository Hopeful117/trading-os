package com.hope.trading.gateway;

import com.hope.trading.gateway.security.JwtProperties;
import com.hope.trading.gateway.security.AgentJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AgentJwtProperties.class})
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
