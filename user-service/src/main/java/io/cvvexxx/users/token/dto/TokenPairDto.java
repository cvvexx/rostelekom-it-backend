package io.cvvexxx.users.token.dto;

import java.util.UUID;

public record TokenPairDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId
) {
    public static final String BEARER = "Bearer";
}
