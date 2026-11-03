package io.cvvexxx.users.otp.service;

import io.cvvexxx.users.otp.dto.OtpChallengeDto;

import java.time.Duration;
import java.util.Optional;

public interface OtpService {

    OtpChallengeDto requestCode(String normalizedPhone);

    void verifyCode(String normalizedPhone, String code);

    String issueRegistrationToken(String normalizedPhone);

    Optional<String> consumeRegistrationToken(String token);

    Duration registrationTtl();
}
