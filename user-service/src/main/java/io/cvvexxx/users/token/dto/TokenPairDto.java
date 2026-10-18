package io.cvvexxx.users.token.dto;

import java.util.UUID;

//Dto для выдачи токенов юзерам
public record TokenPairDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId
) {
    public static final String BEARER = "Bearer";
}
