package com.equiflow.auth.service;

import com.equiflow.auth.dto.AuthRequest;
import com.equiflow.auth.dto.AuthResponse;
import com.equiflow.auth.dto.UserResponse;
import com.equiflow.auth.model.User;
import com.equiflow.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse issueToken(AuthRequest request) {
        log.info("Authentication attempt for user: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getUsername());
                    return new BadCredentialsException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password for user: {}", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        Instant expiresAt = jwtService.extractExpiration(token);

        log.info("Token issued for user: {} with role: {}", user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }

    public boolean validateToken(String token) {
        return jwtService.isTokenValid(token);
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .collect(Collectors.toList());
    }
}
