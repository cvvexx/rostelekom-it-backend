package io.cvvexxx.users.auth.dto;

import java.util.UUID;

//Dto для выдачи токенов юзерам
public record TokenPairDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) implements VerifyResultDto
{
    public static final String BEARER = "Bearer";
}
