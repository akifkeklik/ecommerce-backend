package com.ecommerce.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(
                tokenProvider,
                "jwtSecret",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", 15 * 60 * 1000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", 7 * 24 * 60 * 60 * 1000L);
    }

    @Test
    void accessTokenMustNotBeAcceptedAsRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken("test-user");

        assertThat(tokenProvider.validateAccessToken(accessToken)).isTrue();
        assertThat(tokenProvider.validateRefreshToken(accessToken)).isFalse();
    }

    @Test
    void refreshTokenMustNotAuthenticateApiRequests() {
        String refreshToken = tokenProvider.generateRefreshToken("test-user");

        assertThat(tokenProvider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(tokenProvider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    void tokenSubjectCanBeReadAfterSignatureValidation() {
        String accessToken = tokenProvider.generateAccessToken("test-user");

        assertThat(tokenProvider.getUsernameFromToken(accessToken)).isEqualTo("test-user");
    }
}
