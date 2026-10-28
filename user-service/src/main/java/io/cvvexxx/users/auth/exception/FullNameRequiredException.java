package io.cvvexxx.users.auth.exception;

import io.cvvexxx.users.advice.LocalizedException;

//Новый номер пытается войти без фамилии/имени - регистрация без ФИО запрещена
public class FullNameRequiredException extends LocalizedException {

    public FullNameRequiredException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
