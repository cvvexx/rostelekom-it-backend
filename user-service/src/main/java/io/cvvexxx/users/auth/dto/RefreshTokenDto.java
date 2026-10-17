package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenDto(
        @NotBlank(message = "Refresh-токен обязателен")
        String refreshToken
) {
}
