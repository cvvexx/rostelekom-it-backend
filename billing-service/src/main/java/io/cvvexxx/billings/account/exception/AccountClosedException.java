package io.cvvexxx.billings.account.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class AccountClosedException extends LocalizedException {
    public AccountClosedException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
