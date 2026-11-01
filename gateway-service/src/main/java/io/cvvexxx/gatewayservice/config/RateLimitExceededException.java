package io.cvvexxx.gatewayservice.config;

//Бросается из RateLimitFilter, в ответ HTTP 429 превращает SecurityExceptionHandler
public class RateLimitExceededException extends RuntimeException {
}
