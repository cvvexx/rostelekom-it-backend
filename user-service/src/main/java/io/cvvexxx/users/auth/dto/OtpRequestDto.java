package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//DTO для получения одноразового кода по телефону
public record OtpRequestDto(
        @NotBlank(message = "Номер телефона обязателен")
        @Size(max = 20, message = "Номер телефона слишком длинный")
        String phone
) {
}
