package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.*;
import com.hope.trading.trading_core.helper.UserMapper;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.security.JwtService;
import com.hope.trading.trading_core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;



    /**
     * Create a user
     * @param userRequest contains the desired user attributes
     * @return user as a DTO
     */
    @PostMapping("/register")
    public ResponseEntity<?> createUser(@RequestBody @Valid UserRequest userRequest) {
        userService.createUser(userRequest);
        return ResponseEntity.ok("User successfully created");
    }



    /**
     * Fetch a user
     * @param id id of the desired user
     * @return user as a DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @RequestBody LoginRequest request
    ){


        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        User user = userService.getUserByUsername(request.getUsername());
        UserDto userDto=userMapper.toDto(user);
        AuthenticationResponse authenticationResponse= AuthenticationResponse.builder()
                .token(jwtService.generateToken(userDto))
                .build();

        return ResponseEntity.ok(authenticationResponse);



    }
}
