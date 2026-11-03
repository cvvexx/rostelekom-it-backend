package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.*;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;

public interface AuthService {

    TokenPairDto registerUser(RegisterDto dto);

    OtpChallengeDto requestOtp(OtpRequestDto dto);

    VerifyResultDto verifyOtp(OtpVerifyDto dto);

    TokenPairDto refresh(String refreshToken);

    void logout(String refreshToken);

    ServiceTokenResponseDto getServiceToken(ServiceTokenRequestDto dto);
}
