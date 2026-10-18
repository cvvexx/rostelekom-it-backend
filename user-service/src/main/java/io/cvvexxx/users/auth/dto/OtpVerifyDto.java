package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

//DTO для подтверждения кода
//TODO(Переписать все захардкоженные message)
public record OtpVerifyDto(
        @NotBlank(message = "Номер телефона обязателен") //Не null, не пустая и не из одних пробелов
        @Size(max = 20, message = "Номер телефона слишком длинный") //Как Field(max_length=20)
        String phone,

        @NotBlank(message = "Код обязателен")
        @Pattern(regexp = "\\d{4,8}", message = "Код должен состоять из 4-8 цифр") //Как Field(pattern=...)
        String code
) {
}
