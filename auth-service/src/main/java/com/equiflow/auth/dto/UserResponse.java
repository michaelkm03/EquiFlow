package com.equiflow.auth.dto;

import com.equiflow.auth.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User details response")
public class UserResponse {

    @Schema(description = "User ID")
    private UUID id;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "User role")
    private Role role;

    @Schema(description = "Account creation time")
    private Instant createdAt;

    @Schema(description = "Last login time")
    private Instant lastLoginAt;
}
