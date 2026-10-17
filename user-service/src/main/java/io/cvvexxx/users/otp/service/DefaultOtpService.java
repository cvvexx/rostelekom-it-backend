package io.cvvexxx.users.otp.service;

import io.cvvexxx.users.otp.config.OtpProperties;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.exception.OtpRequestNotAllowedException;
import io.cvvexxx.users.otp.store.OtpStore;
import io.cvvexxx.users.otp.store.OtpVerification;
import io.cvvexxx.users.phone.PhoneNumbers;
import io.cvvexxx.users.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultOtpService implements OtpService {

    private static final String SMS_TEMPLATE = "Код для входа: %s. Никому его не сообщайте.";

    private final OtpStore otpStore;
    private final SmsSender smsSender;
    private final OtpProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public OtpChallengeDto requestCode(String normalizedPhone) {
        if (!otpStore.tryAcquireSendPermit(normalizedPhone, properties.resendCooldown())) {
            Duration retryAfter = otpStore.sendPermitTimeToLive(normalizedPhone);
            throw new OtpRequestNotAllowedException(retryAfter,
                    "Код уже отправлен, повторите через %d с".formatted(retryAfter.toSeconds()));
        }

        long sends = otpStore.incrementSendsInWindow(normalizedPhone, properties.window());
        if (sends > properties.maxSendsPerWindow()) {
            throw new OtpRequestNotAllowedException(properties.window(),
                    "Слишком много запросов кода, попробуйте позже");
        }

        String code = generateCode();
        otpStore.save(normalizedPhone, code, properties.ttl());
        smsSender.send(normalizedPhone, SMS_TEMPLATE.formatted(code));

        log.info("OTP issued for phone {}", PhoneNumbers.mask(normalizedPhone));

        return new OtpChallengeDto(
                normalizedPhone,
                properties.exposeCode() ? code : null,
                properties.ttl().toSeconds(),
                properties.resendCooldown().toSeconds()
        );
    }

    @Override
    public void verifyCode(String normalizedPhone, String code) {
        OtpVerification verification = otpStore.verify(normalizedPhone, code, properties.maxAttempts());

        switch (verification.outcome()) {
            case OK -> {
            }
            case NOT_REQUESTED -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.NOT_REQUESTED, 0, "Код не запрашивался или истёк");
            case MISMATCH -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.MISMATCH, verification.attemptsLeft(), "Неверный код");
            case ATTEMPTS_EXCEEDED -> throw new InvalidOtpException(
                    InvalidOtpException.Reason.ATTEMPTS_EXCEEDED, 0,
                    "Превышено число попыток, запросите код заново");
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(properties.length());
        for (int i = 0; i < properties.length(); i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

}
