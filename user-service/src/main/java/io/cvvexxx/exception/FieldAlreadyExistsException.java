package io.cvvexxx.exception;

import lombok.Getter;

@Getter
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