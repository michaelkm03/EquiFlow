package com.equiflow.auth;

import com.equiflow.auth.dto.AuthRequest;
import com.equiflow.auth.dto.AuthResponse;
import com.equiflow.auth.model.Role;
import com.equiflow.auth.model.User;
import com.equiflow.auth.repository.UserRepository;
import com.equiflow.auth.service.AuthService;
import com.equiflow.auth.service.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class AuthServiceTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeMethod
    public void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        // Use a known long secret for tests
        jwtService = new JwtService();
        setField(jwtService, "secret",
            "equiflow-test-secret-key-minimum-256-bits-long-for-hmac-sha256-unit-test");
        setField(jwtService, "expirationMs", 3600000L);
        authService = new AuthService(userRepository, jwtService, passwordEncoder);
    }

    @Test(description = "Valid login returns a non-null JWT token")
    public void testValidLoginReturnsJwt() {
        String rawPassword = "password123";
        String hashed = passwordEncoder.encode(rawPassword);

        User user = User.builder()
                .id(UUID.randomUUID())
                .username("trader1")
                .passwordHash(hashed)
                .role(Role.TRADER)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsername("trader1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthRequest request = new AuthRequest("trader1", rawPassword);
        AuthResponse response = authService.issueToken(request);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getToken(), "Token should not be null");
        assertFalse(response.getToken().isEmpty(), "Token should not be empty");
        assertEquals(response.getRole(), Role.TRADER, "Role should be TRADER");
        assertEquals(response.getUsername(), "trader1", "Username should match");
        assertNotNull(response.getExpiresAt(), "ExpiresAt should not be null");
        assertTrue(response.getExpiresAt().isAfter(Instant.now()), "Token should not already be expired");
    }

    @Test(description = "Invalid password throws BadCredentialsException",
          expectedExceptions = BadCredentialsException.class)
    public void testInvalidPasswordThrows() {
        String hashed = passwordEncoder.encode("correctPassword");
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("trader1")
                .passwordHash(hashed)
                .role(Role.TRADER)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsername("trader1")).thenReturn(Optional.of(user));

        AuthRequest request = new AuthRequest("trader1", "wrongPassword");
        authService.issueToken(request);
    }

    @Test(description = "Unknown user throws BadCredentialsException",
          expectedExceptions = BadCredentialsException.class)
    public void testUnknownUserThrows() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        authService.issueToken(new AuthRequest("unknown", "password123"));
    }

    @Test(description = "Generated token is valid")
    public void testGeneratedTokenIsValid() {
        String rawPassword = "password123";
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("trader1")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.TRADER)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsername("trader1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = authService.issueToken(new AuthRequest("trader1", rawPassword));
        assertTrue(authService.validateToken(response.getToken()), "Issued token must be valid");
    }

    // Reflection helper to set private fields in tests
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
