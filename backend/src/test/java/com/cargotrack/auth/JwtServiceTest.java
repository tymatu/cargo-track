package com.cargotrack.auth;

import com.cargotrack.config.JwtProperties;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-0123456789-0123456789-0123456789-0123456789-0123456789";

    private JwtService jwtService;

    private final User user = User.builder()
            .id(42L)
            .email("user@test.io")
            .role(Role.USER)
            .build();

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14)));
    }

    @Test
    void generatedTokenContainsExpectedClaims() {
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parse(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("user@test.io");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(
                new JwtProperties(SECRET, Duration.ofMillis(-1000), Duration.ofDays(14)));
        String token = shortLived.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties(
                "another-secret-9876543210-9876543210-9876543210-9876543210-987654",
                Duration.ofMinutes(15), Duration.ofDays(14)));
        String token = other.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
