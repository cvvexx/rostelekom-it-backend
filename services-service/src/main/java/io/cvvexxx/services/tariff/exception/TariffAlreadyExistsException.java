package io.cvvexxx.services.tariff.exception;

import io.cvvexxx.services.advice.LocalizedException;

public class TariffAlreadyExistsException extends LocalizedException {
    public TariffAlreadyExistsException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}