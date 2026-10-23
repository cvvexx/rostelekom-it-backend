package io.cvvexxx.users.user.exception;

import io.cvvexxx.users.advice.LocalizedException;
import lombok.Getter;

//Кастомный Exception, чтобы было проще обрабатывать ошибки
@Getter //Lombok: генерирует геттеры для полей
public class FieldAlreadyExistsException extends LocalizedException {

    private final String fieldName;
    private final Object fieldValue;

    public FieldAlreadyExistsException(String fieldName, Object fieldValue, String messageCode, Object... args) {
        super(messageCode, args);
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}