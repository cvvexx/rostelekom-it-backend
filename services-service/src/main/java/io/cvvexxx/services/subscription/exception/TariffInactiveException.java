package io.cvvexxx.services.subscription.exception;

import io.cvvexxx.services.advice.LocalizedException;

import java.util.UUID;

public class TariffInactiveException extends LocalizedException {
    public TariffInactiveException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
