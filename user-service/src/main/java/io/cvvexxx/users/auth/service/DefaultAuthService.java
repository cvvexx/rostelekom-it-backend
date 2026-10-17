package io.cvvexxx.users.auth.service;

import io.cvvexxx.users.auth.dto.OtpRequestDto;
import io.cvvexxx.users.auth.dto.OtpVerifyDto;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.service.OtpService;
import io.cvvexxx.users.phone.PhoneNumbers;
import io.cvvexxx.users.token.dto.TokenPairDto;
import io.cvvexxx.users.token.service.TokenService;
import io.cvvexxx.users.user.entity.User;
import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAuthService implements AuthService {

    private final OtpService otpService;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public OtpChallengeDto requestOtp(OtpRequestDto dto) {
        String phone = PhoneNumbers.normalize(dto.phone());
        log.info("OTP requested for phone {}", PhoneNumbers.mask(phone));

        return otpService.requestCode(phone);
    }

    @Override
    @Transactional
    public TokenPairDto verifyOtp(OtpVerifyDto dto) {
        String phone = PhoneNumbers.normalize(dto.phone());
        otpService.verifyCode(phone, dto.code());

        User user = userService.findOrCreateByPhone(phone);
        log.info("User {} authenticated by phone {}", user.getId(), PhoneNumbers.mask(phone));

        return tokenService.issueTokens(user);
    }

    @Override
    public TokenPairDto refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }
}
