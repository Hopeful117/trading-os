package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    void createUser(UserRequest request);

    User getUserById(UUID userId);

    User getUserByUsername(String username);

    List<User> getAllUsers();

    void deleteUser(UUID userId);
}
