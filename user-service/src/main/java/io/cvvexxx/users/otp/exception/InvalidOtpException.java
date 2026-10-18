package io.cvvexxx.users.otp.exception;

import lombok.Getter;

@Getter //Lombok: генерирует геттеры getReason(), getAttemptsLeft()
public class InvalidOtpException extends RuntimeException {

    public enum Reason {
        NOT_REQUESTED,
        EXPIRED,
        MISMATCH,
        ATTEMPTS_EXCEEDED
    }

    private final Reason reason;
    private final int attemptsLeft;

    public InvalidOtpException(Reason reason, int attemptsLeft, String message) {
        super(message);
        this.reason = reason;
        this.attemptsLeft = attemptsLeft;
    }
}
