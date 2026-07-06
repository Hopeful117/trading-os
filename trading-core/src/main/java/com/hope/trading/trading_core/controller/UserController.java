package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for managing user-related operations.
 * Provides endpoints for creating and retrieving users.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    /**
     * Create a user
     * @param userRequest contains the desired user attributes
     * @return user as a DTO
     */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserRequest userRequest) {
        UserDto userDto = userService.createUser(userRequest);
        return ResponseEntity.ok(userDto);
    }

    /**
     * Fetch a user
     * @param id id of the desired user
     * @return user as a DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID id) {
        UserDto userDto = userService.getUser(id);
        return ResponseEntity.ok(userDto);
    }
}
