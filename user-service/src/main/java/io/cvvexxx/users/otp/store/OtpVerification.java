package io.cvvexxx.users.otp.store;

public record OtpVerification(Outcome outcome, int attemptsLeft) {

    public enum Outcome {
        OK,
        NOT_REQUESTED,
        MISMATCH,
        ATTEMPTS_EXCEEDED
    }

    public static OtpVerification ok() {
        return new OtpVerification(Outcome.OK, 0);
    }

    public static OtpVerification notRequested() {
        return new OtpVerification(Outcome.NOT_REQUESTED, 0);
    }

    public static OtpVerification mismatch(int attemptsLeft) {
        return new OtpVerification(Outcome.MISMATCH, attemptsLeft);
    }

    public static OtpVerification attemptsExceeded() {
        return new OtpVerification(Outcome.ATTEMPTS_EXCEEDED, 0);
    }
}
