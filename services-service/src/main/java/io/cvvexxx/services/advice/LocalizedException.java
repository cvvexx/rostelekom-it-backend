package io.cvvexxx.services.advice;

import lombok.Getter;

@Getter
public abstract class LocalizedException extends RuntimeException {

    private final Object[] args;

    protected LocalizedException(String messageCode, Object... args) {
        super(messageCode);
        this.args = args;
    }
}