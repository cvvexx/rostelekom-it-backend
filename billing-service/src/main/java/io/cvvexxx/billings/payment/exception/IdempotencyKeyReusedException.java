package io.cvvexxx.billings.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//Тот же Idempotency-Key пришёл с другим телом запроса
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String message) {
        super(message);
    }
}
