package io.cvvexxx.services.subscription.exception;

import io.cvvexxx.services.advice.LocalizedException;

import java.util.UUID;

public class SubscriptionAlreadyExistsException extends LocalizedException {
    public SubscriptionAlreadyExistsException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
