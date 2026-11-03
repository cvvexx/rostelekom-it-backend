package io.cvvexxx.users.auth.dto;

//Результат /otp/verify: либо токены (юзер уже есть), либо токен регистрации (номер новый)
//sealed - реализаций ровно две и других не будет, контроллер различает их через instanceof
public sealed interface VerifyResultDto permits TokenPairDto, RegistrationRequiredDto {
}
