package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserDto createUser(UserRequest request);

    UserDto getUserById(UUID userId);

    UserDto getUserByUsername(String username);

    List<UserDto> getAllUsers();

    void deleteUser(UUID userId);
}
