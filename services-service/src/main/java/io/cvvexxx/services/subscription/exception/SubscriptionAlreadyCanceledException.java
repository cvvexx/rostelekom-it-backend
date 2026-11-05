package io.cvvexxx.services.subscription.exception;

import io.cvvexxx.services.advice.LocalizedException;

import java.util.UUID;

public class SubscriptionAlreadyCanceledException extends LocalizedException {
    public SubscriptionAlreadyCanceledException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
