package io.cvvexxx.services.subscription.exception;

import io.cvvexxx.services.advice.LocalizedException;

public class ChargeDeclinedException extends LocalizedException {
    public ChargeDeclinedException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
