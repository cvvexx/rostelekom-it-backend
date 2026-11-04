package io.cvvexxx.services.subscription.exception;

import io.cvvexxx.services.advice.LocalizedException;

import java.util.UUID;

public class SubscriptionNotFoundException extends LocalizedException {
    public SubscriptionNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
