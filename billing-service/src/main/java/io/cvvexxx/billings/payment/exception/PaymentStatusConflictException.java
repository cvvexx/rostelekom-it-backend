package io.cvvexxx.billings.payment.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class PaymentStatusConflictException extends LocalizedException {
    public PaymentStatusConflictException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
