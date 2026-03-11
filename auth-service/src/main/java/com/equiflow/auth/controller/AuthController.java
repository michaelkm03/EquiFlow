package com.equiflow.auth.controller;

import com.equiflow.auth.dto.AuthRequest;
import com.equiflow.auth.dto.AuthResponse;
import com.equiflow.auth.dto.UserResponse;
import com.equiflow.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT token issuance and validation endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token")
    @Operation(
        summary = "Issue JWT token",
        description = "Authenticates user credentials and returns a JWT bearer token",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                examples = @ExampleObject(
                    value = "{\"username\": \"trader1\", \"password\": \"password123\"}"
                )
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token issued successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public ResponseEntity<AuthResponse> issueToken(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.issueToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    @Operation(
        summary = "Validate JWT token",
        description = "Validates the provided JWT bearer token",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    public ResponseEntity<Map<String, Object>> validateToken(
            @Parameter(description = "Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "error", "Missing or malformed token"));
        }
        String token = authHeader.substring(7);
        boolean valid = authService.validateToken(token);
        if (valid) {
            return ResponseEntity.ok(Map.of("valid", true, "timestamp", Instant.now()));
        } else {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "error", "Token invalid or expired"));
        }
    }

    @GetMapping("/users")
    @Operation(
        summary = "List all users",
        description = "Returns all registered users (REGULATOR role required)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users returned successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(authService.getUsers());
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(
        summary = "Get JWKS",
        description = "Returns JSON Web Key Set for token verification by other services"
    )
    public ResponseEntity<Map<String, Object>> getJwks() {
        // In production, this would return the actual public key set
        // For HMAC-SHA256 (symmetric), this endpoint is informational
        return ResponseEntity.ok(Map.of(
            "keys", List.of(),
            "algorithm", "HS256",
            "note", "Symmetric HMAC-SHA256 - use shared JWT_SECRET for validation"
        ));
    }
}
