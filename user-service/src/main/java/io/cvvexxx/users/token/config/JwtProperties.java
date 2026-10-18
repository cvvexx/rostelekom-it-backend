package io.cvvexxx.users.token.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated//Приложение не запуститься, если не пройдет валидация(@NotBlank над issuer)
@ConfigurationProperties(prefix = "app.jwt") //Читает app.jwt.* из .properties, как pydantic BaseSettings
public record JwtProperties(
        @NotBlank //Обязательное поле в конфиге
        String issuer,
        String audience,

        Duration accessTokenTtl,

        Duration refreshTokenTtl,

        String keyId,

        String privateKeyLocation
) {
    public JwtProperties {
        if (audience == null || audience.isBlank()) {
            audience = "rostelekom";//TODO(убрать хардкод)
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
