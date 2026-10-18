package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//DTO для получения одноразового кода по телефону
public record OtpRequestDto(
        //Аннотации валидации - аналог Field(...) в pydantic, message - текст ошибки, который уйдет в ответе
        //Проверяются только если в контроллере перед параметром стоит @Valid
        @NotBlank(message = "Номер телефона обязателен") //Не null, не пустая и не из одних пробелов
        @Size(max = 20, message = "Номер телефона слишком длинный") //Длина строки <= 20, как Field(max_length=20)
        String phone
) {
}
