package com.hope.trading.gateway.feignClient;

import com.hope.trading.gateway.dto.LoginRequest;
import com.hope.trading.gateway.dto.UserAuthenticationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "trading-core")
public interface UserClient  {

    @PostMapping("/internal/users/authenticate")
    UserAuthenticationDto authenticate(
            @RequestBody LoginRequest request
    );

}
