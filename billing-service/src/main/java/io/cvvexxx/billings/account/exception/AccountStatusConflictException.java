package io.cvvexxx.billings.account.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class AccountStatusConflictException extends LocalizedException {
    public AccountStatusConflictException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
