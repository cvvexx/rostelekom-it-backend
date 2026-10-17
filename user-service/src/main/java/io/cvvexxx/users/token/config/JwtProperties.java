package io.cvvexxx.users.token.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String audience,

        Duration accessTokenTtl,

        Duration refreshTokenTtl,

        String keyId,

        String privateKeyLocation
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "http://localhost:8081";
        }
        if (audience == null || audience.isBlank()) {
            audience = "rostelekom";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(30);
        }
        if (keyId == null || keyId.isBlank()) {
            keyId = "user-service-key";
        }
    }
}
