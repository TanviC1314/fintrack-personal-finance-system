package com.fintrack.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret     HMAC signing secret, at least 32 characters. Supplied via the JWT_SECRET
 *                   environment variable; there is deliberately no default.
 * @param expiration lifetime of an access token
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {
}
