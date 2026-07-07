package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.helper.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private UUID userId;
    private String username;
    private String email;
    private Role role;
}
