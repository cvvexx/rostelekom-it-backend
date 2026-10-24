package io.cvvexxx.billings.payment.exception;

import io.cvvexxx.billings.advice.LocalizedException;

public class PaymentNotFoundException extends LocalizedException {
    public PaymentNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
