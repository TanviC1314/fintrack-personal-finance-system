package com.fintrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-comfortably-over-32-bytes";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final UUID userId = UUID.randomUUID();

    private JwtService serviceAt(Instant instant, String secret) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(1)), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenIdentifiesTheUser() {
        JwtService service = serviceAt(NOW, SECRET);

        AuthenticatedUser user = service.parse(service.generateToken(userId, "alice@example.com"));

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("alice@example.com");
    }

    @Test
    void tokenIsAcceptedUntilItsExpiryAndRejectedAfter() {
        String token = serviceAt(NOW, SECRET).generateToken(userId, "alice@example.com");

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(59)), SECRET).parse(token).id()).isEqualTo(userId);
        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofHours(1)).plusSeconds(1), SECRET).parse(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = serviceAt(NOW, "a-completely-different-secret-of-sufficient-length")
                .generateToken(userId, "alice@example.com");

        assertThatThrownBy(() -> serviceAt(NOW, SECRET).parse(forged)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService service = serviceAt(NOW, SECRET);
        String[] parts = service.generateToken(userId, "alice@example.com").split("\\.");
        String otherPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"exp\":9999999999}").getBytes());

        String tampered = parts[0] + "." + otherPayload + "." + parts[2];

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService service = serviceAt(NOW, SECRET);

        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.parse("")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void secretShorterThan256BitsIsRefusedAtStartup() {
        assertThatThrownBy(() -> serviceAt(NOW, "too-short")).isInstanceOf(WeakKeyException.class);
    }
}
