package io.cvvexxx.users.otp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

//Достает проперти с префиксом из .properties файла
//Аналог pydantic BaseSettings с префиксом: app.otp.resend-cooldown=60s из .properties -> поле resendCooldown,
//строки типа 60s, 5m сами конвертируются в Duration
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        int length,

        Duration ttl,

        Duration resendCooldown,

        Duration window,
        int maxSendsPerWindow,

        int maxAttempts,

        boolean exposeCode,

        String store
) {
    public OtpProperties {
        if (length <= 0) {
            length = 6;
        }
        if (ttl == null) {
            ttl = Duration.ofMinutes(5);
        }
        if (resendCooldown == null) {
            resendCooldown = Duration.ofSeconds(60);
        }
        if (window == null) {
            window = Duration.ofHours(1);
        }
        if (maxSendsPerWindow <= 0) {
            maxSendsPerWindow = 5;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (store == null || store.isBlank()) {
            store = "redis";
        }
    }
}
