package io.cvvexxx.users.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

//JSON который отдает приложение при требовании кода
//Настройка @JsonInclude сделана для того, чтобы в проде поле code не возвращалось
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpChallengeDto(
        String phone,
        String code,
        long expiresInSeconds,
        long resendAfterSeconds
) {
}
