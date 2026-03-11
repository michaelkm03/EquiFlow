package com.equiflow.auth.dto;

import com.equiflow.auth.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT token")
public class AuthResponse {

    @Schema(description = "JWT bearer token")
    private String token;

    @Schema(description = "Token expiration time")
    private Instant expiresAt;

    @Schema(description = "User role")
    private Role role;

    @Schema(description = "Username")
    private String username;
}
