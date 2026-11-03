package io.cvvexxx.users.auth.dto;

//Ответ /otp/verify для нового номера: код верный, но юзера еще нет
//registrationToken одноразовый, фронт отправляет его в /api/auth/register вместе с ФИО
//expiresInSeconds - сколько живет токен, после этого надо запрашивать код заново
public record RegistrationRequiredDto(
        String registrationToken,
        long expiresInSeconds
) implements VerifyResultDto {
}
