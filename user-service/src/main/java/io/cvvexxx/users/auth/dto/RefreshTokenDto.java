package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;

//DTO для рефреша токенов
public record RefreshTokenDto(
        @NotBlank(message = "Refresh-токен обязателен")
        String refreshToken
) {
}
