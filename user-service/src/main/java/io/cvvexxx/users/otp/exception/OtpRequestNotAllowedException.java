package io.cvvexxx.users.otp.exception;

import lombok.Getter;

import java.time.Duration;

@Getter //Lombok: генерирует геттер getRetryAfter()
public class OtpRequestNotAllowedException extends RuntimeException {

    private final Duration retryAfter;

    public OtpRequestNotAllowedException(Duration retryAfter, String message) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
