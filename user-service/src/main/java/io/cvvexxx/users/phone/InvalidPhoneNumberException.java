package io.cvvexxx.users.phone;

import io.cvvexxx.users.advice.LocalizedException;

public class InvalidPhoneNumberException extends LocalizedException {

    public InvalidPhoneNumberException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
