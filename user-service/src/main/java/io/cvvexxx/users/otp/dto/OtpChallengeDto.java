package io.cvvexxx.users.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

//JSON который отдает приложение при требовании кода
//Настройка @JsonInclude сделана для того, чтобы в проде поле code не возвращалось
//registered - есть ли уже юзер с этим номером, если false, то фронт показывает поля ФИО вместе с полем кода
//(заполняет DefaultAuthService, OtpService про юзеров не знает и оставляет null)
@JsonInclude(JsonInclude.Include.NON_NULL) //null-поля не попадают в JSON, как response_model_exclude_none=True
public record OtpChallengeDto(
        String phone,
        String code,
        long expiresInSeconds,
        long resendAfterSeconds,
        Boolean registered
) {
    public OtpChallengeDto withRegistered(boolean registered) {
        return new OtpChallengeDto(phone, code, expiresInSeconds, resendAfterSeconds, registered);
    }
}
