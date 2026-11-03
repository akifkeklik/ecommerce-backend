package com.ecommerce.application.service;

import com.ecommerce.application.dto.auth.*;
import com.ecommerce.domain.exception.DomainException;
import com.ecommerce.domain.user.Role;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.UserRepository;
import com.ecommerce.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for authentication operations.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Registers a new user.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DomainException("Email is already registered");
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DomainException("Username is already taken");
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(Role.CUSTOMER);
        user.setActive(true);

        // Generate tokens before the first save so the refresh token is persisted
        // together with the new user in a single repository operation.
        String accessToken = tokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());
        user.addRefreshToken(refreshToken);

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getUsername());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Authenticates a user.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authenticate
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()));

        User user = (User) authentication.getPrincipal();
        user.recordSuccessfulLogin();

        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        user.addRefreshToken(refreshToken);
        userRepository.save(user);

        log.info("User logged in: {}", user.getUsername());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Refreshes access token using refresh token.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw new DomainException("Invalid or expired refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DomainException("User not found"));

        if (!user.hasRefreshToken(refreshToken)) {
            throw new DomainException("Refresh token not recognized");
        }

        // Remove old refresh token and generate new ones
        user.removeRefreshToken(refreshToken);
        String newAccessToken = tokenProvider.generateAccessToken(username);
        String newRefreshToken = tokenProvider.generateRefreshToken(username);
        user.addRefreshToken(newRefreshToken);
        userRepository.save(user);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    /**
     * Logs out a user (invalidates refresh token).
     */
    @Transactional
    public void logout(String refreshToken) {
        if (tokenProvider.validateToken(refreshToken)) {
            String username = tokenProvider.getUsernameFromToken(refreshToken);
            userRepository.findByUsername(username).ifPresent(user -> {
                user.removeRefreshToken(refreshToken);
                userRepository.save(user);
            });
        }
    }

    /**
     * Logs out from all devices.
     */
    @Transactional
    public void logoutAll(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.clearAllRefreshTokens();
            userRepository.save(user);
        });
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponse.UserDto userDto = new AuthResponse.UserDto(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.getAvatarUrl());

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                tokenProvider.getAccessTokenExpiration() / 1000,
                userDto);
    }
}
