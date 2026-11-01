package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;

//DTO для выпуска сервисного токена, секреты клиентов лежат в app.jwt.service-clients.<clientId>
public record ServiceTokenRequestDto(
        @NotBlank(message = "{validation.service-token.client-id.required}")
        String clientId,
        @NotBlank(message = "{validation.service-token.client-secret.required}")
        String clientSecret
) {
}
