package io.cvvexxx.billings.account.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class AccountHasPendingPaymentsException extends LocalizedException {
    public AccountHasPendingPaymentsException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
