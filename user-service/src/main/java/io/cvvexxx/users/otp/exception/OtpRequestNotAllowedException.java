package io.cvvexxx.users.otp.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class OtpRequestNotAllowedException extends RuntimeException {

    private final Duration retryAfter;

    public OtpRequestNotAllowedException(Duration retryAfter, String message) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
