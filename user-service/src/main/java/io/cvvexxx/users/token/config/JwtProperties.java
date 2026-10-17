package io.cvvexxx.users.token.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank
        String issuer,
        String audience,

        Duration accessTokenTtl,

        Duration refreshTokenTtl,

        String keyId,

        String privateKeyLocation
) {
    public JwtProperties {
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
