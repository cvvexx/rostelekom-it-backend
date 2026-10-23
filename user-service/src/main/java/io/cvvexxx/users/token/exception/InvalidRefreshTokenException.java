package io.cvvexxx.users.token.exception;

import io.cvvexxx.users.advice.LocalizedException;

public class InvalidRefreshTokenException extends LocalizedException {

    public InvalidRefreshTokenException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
