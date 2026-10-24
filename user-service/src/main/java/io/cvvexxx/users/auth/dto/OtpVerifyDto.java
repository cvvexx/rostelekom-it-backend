package io.cvvexxx.users.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

//DTO для подтверждения кода
public record OtpVerifyDto(
        @NotBlank(message = "{validation.otp-verify.phone.required}") //Не null, не пустая и не из одних пробелов
        @Size(max = 20, message = "{validation.otp-verify.phone.too-long}") //Как Field(max_length=20)
        String phone,

        @NotBlank(message = "{validation.otp-verify.code.required}")
        @Pattern(regexp = "\\d{4,8}", message = "{validation.otp-verify.code.pattern}") //Как Field(pattern=...)
        String code
) {
}
