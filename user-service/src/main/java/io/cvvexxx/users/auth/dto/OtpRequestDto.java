package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequestDto(
        @NotBlank(message = "Номер телефона обязателен")
        @Size(max = 20, message = "Номер телефона слишком длинный")
        String phone
) {
}
