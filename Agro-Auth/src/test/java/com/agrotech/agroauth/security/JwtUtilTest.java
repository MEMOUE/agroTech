package com.agrotech.agroauth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "TestSecretKeyForJwtUtilTests_MustBe32Chars!!",
                86400000L
        );
    }

    @Test
    void generateToken_andExtractUsername() {
        String token = jwtUtil.generateToken("john", "AGRICULTEUR");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("john");
    }

    @Test
    void generateToken_andExtractRole() {
        String token = jwtUtil.generateToken("john", "ADMIN");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken("john", "AGRICULTEUR");

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        String token = jwtUtil.generateToken("john", "AGRICULTEUR");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        JwtUtil shortLivedJwt = new JwtUtil(
                "TestSecretKeyForJwtUtilTests_MustBe32Chars!!",
                -1L
        );
        String token = shortLivedJwt.generateToken("john", "AGRICULTEUR");

        assertThat(shortLivedJwt.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForEmptyString() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForRandomString() {
        assertThat(jwtUtil.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void differentUsersGetDifferentTokens() {
        String token1 = jwtUtil.generateToken("alice", "AGRICULTEUR");
        String token2 = jwtUtil.generateToken("bob", "AGRICULTEUR");

        assertThat(token1).isNotEqualTo(token2);
        assertThat(jwtUtil.extractUsername(token1)).isEqualTo("alice");
        assertThat(jwtUtil.extractUsername(token2)).isEqualTo("bob");
    }
}
