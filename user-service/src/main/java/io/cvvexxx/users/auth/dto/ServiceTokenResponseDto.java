package io.cvvexxx.users.auth.dto;

//Ответ с сервисным токеном, expiresInSeconds - через сколько секунд токен истечет
public record ServiceTokenResponseDto(
        String accessToken,
        long expiresInSeconds
) {
}
