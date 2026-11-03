package io.cvvexxx.services.tariff.exception;

import io.cvvexxx.services.advice.LocalizedException;

public class TariffNotFoundException extends LocalizedException {
    public TariffNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
