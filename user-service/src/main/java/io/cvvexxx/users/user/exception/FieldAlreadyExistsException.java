package io.cvvexxx.users.user.exception;

import lombok.Getter;

//Кастомный Exception, чтобы было проще обрабатывать ошибки
@Getter //Lombok: генерирует геттеры для полей
public class FieldAlreadyExistsException extends RuntimeException {

    private final String fieldName;
    private final Object fieldValue;

    public FieldAlreadyExistsException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
        this.fieldValue = null;
    }

    public FieldAlreadyExistsException(String fieldName, Object fieldValue, String message) {
        super(message);
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}