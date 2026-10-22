package io.cvvexxx.billings.account.exception;

public class AccountStatusConflictException extends RuntimeException {
    public AccountStatusConflictException(String message) {
        super(message);
    }
}
