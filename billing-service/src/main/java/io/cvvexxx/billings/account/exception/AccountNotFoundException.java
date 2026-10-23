package io.cvvexxx.billings.account.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class AccountNotFoundException extends LocalizedException {
    public AccountNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
