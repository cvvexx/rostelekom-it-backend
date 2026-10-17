package io.cvvexxx.users.otp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpChallengeDto(
        String phone,
        String code,
        long expiresInSeconds,
        long resendAfterSeconds
) {
}
