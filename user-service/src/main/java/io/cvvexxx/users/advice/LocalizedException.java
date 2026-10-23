package io.cvvexxx.users.advice;

import lombok.Getter;

//Базовый exception, у которого message - это ключ из messages.properties, а args - параметры для подстановки.
//Текст для клиента собирает GlobalExceptionHandler через MessageSource
@Getter
public abstract class LocalizedException extends RuntimeException {

    private final Object[] args;

    protected LocalizedException(String messageCode, Object... args) {
        super(messageCode);
        this.args = args;
    }
}
