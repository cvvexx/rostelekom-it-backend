package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.OtpRequestDto;
import io.cvvexxx.users.auth.dto.OtpVerifyDto;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.token.dto.TokenPairDto;

public interface AuthService {

    OtpChallengeDto requestOtp(OtpRequestDto dto);

    TokenPairDto verifyOtp(OtpVerifyDto dto);

    TokenPairDto refresh(String refreshToken);

    void logout(String refreshToken);
}
