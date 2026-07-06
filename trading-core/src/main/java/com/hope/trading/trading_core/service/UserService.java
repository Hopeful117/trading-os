package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;

import java.util.UUID;

public interface UserService {
    UserDto createUser(UserRequest userRequest);
    UserDto getUser(UUID userId);
}
