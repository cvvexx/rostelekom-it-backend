package io.cvvexxx.billings.charges.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class InsufficientFundsException extends LocalizedException {
    public InsufficientFundsException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}