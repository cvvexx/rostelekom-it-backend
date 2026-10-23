package io.cvvexxx.users.otp.exception;

import io.cvvexxx.users.advice.LocalizedException;
import lombok.Getter;

@Getter //Lombok: генерирует геттеры getReason(), getAttemptsLeft()
public class InvalidOtpException extends LocalizedException {

    public enum Reason {
        NOT_REQUESTED,
        EXPIRED,
        MISMATCH,
        ATTEMPTS_EXCEEDED
    }

    private final Reason reason;
    private final int attemptsLeft;

    public InvalidOtpException(Reason reason, int attemptsLeft, String messageCode, Object... args) {
        super(messageCode, args);
        this.reason = reason;
        this.attemptsLeft = attemptsLeft;
    }
}
