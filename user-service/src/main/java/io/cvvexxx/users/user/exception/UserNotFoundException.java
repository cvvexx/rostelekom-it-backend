package io.cvvexxx.users.user.exception;

import io.cvvexxx.users.advice.LocalizedException;

public class UserNotFoundException extends LocalizedException {

    public UserNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
