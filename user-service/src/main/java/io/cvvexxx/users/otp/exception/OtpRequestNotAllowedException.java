package io.cvvexxx.users.otp.exception;

import io.cvvexxx.users.advice.LocalizedException;
import lombok.Getter;

import java.time.Duration;

@Getter //Lombok: генерирует геттер getRetryAfter()
public class OtpRequestNotAllowedException extends LocalizedException {

    private final Duration retryAfter;

    public OtpRequestNotAllowedException(Duration retryAfter, String messageCode, Object... args) {
        super(messageCode, args);
        this.retryAfter = retryAfter;
    }
}
