package io.cvvexxx.users.otp.service;

import io.cvvexxx.users.otp.dto.OtpChallengeDto;

public interface OtpService {

    OtpChallengeDto requestCode(String normalizedPhone);

    void verifyCode(String normalizedPhone, String code);
}
