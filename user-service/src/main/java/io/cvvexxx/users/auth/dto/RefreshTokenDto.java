package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;

//DTO для рефреша токенов
public record RefreshTokenDto(
        @NotBlank(message = "Refresh-токен обязателен") //Не null, не пустая и не из одних пробелов
        String refreshToken
) {
}
