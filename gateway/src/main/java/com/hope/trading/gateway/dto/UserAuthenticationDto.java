package com.hope.trading.gateway.dto;

import com.hope.trading.gateway.helper.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAuthenticationDto {
    private UUID userId;
    private String username;
    private String email;
    private Role role;
}
