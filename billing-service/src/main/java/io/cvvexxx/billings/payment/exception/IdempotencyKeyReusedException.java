package io.cvvexxx.billings.payment.exception;

//Тот же Idempotency-Key пришёл с другим телом запроса
public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String message) {
        super(message);
    }
}
