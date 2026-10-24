package io.cvvexxx.billings.payment.exception;

import io.cvvexxx.billings.advice.LocalizedException;

//Тот же Idempotency-Key пришёл с другим телом запроса
public class IdempotencyKeyReusedException extends LocalizedException {
    public IdempotencyKeyReusedException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
